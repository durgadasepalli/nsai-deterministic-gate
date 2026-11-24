package com.durgaprasad.nsai.eval;

import com.durgaprasad.nsai.local.LocalRuleConnector;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleSet;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs every prompt through every local model several times, checks each answer with the
 * gate and with the independent {@link PolicyOracle}, and writes one CSV row per answer.
 * <pre>
 * ./gradlew :nsai-gate-eval:run --args="--models llama3.2,qwen2.5:1.5b --runs 10"
 * </pre>
 * Options (defaults in brackets): --models [llama3.2] --runs [10] --temperature [0.7]
 * --seed [42] --ollama [http://localhost:11434] --timeout-seconds [300] --json-mode [true]
 * --rules [nsai-gate-demo/src/main/resources/nsai-rules.yaml] --out [build/eval/results.csv]
 */
public final class EvalHarness {

    static final String INTENT = "LOAN_OFFER";

    /** Same wording as the demo's LoanOfferAssistant, so results describe the shipped demo. */
    static final String PROMPT_TEMPLATE = """
            You are an assistant for Example Bank drafting personal-loan offers.
            Reply with a single JSON object and nothing else, using exactly these fields:
              "clientId"     (string),
              "interestRate" (annual rate as a string with %%, e.g. "7.5%%"),
              "termMonths"   (integer),
              "currency"     (ISO code, e.g. "USD"),
              "message"      (one friendly sentence to the customer).
            Customer request: %s
            """;

    record Prompt(String id, String category, String request) { }

    record Row(String model, Prompt prompt, int run, long seed, long latencyMs, String output, String error,
               PolicyOracle.Verdict oracle, GateCheck.Outcome gate, PolicyOracle.Verdict oracleAfterRepair) { }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        List<String> models = Arrays.stream(opts.getOrDefault("models", "llama3.2").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        int runs = Integer.parseInt(opts.getOrDefault("runs", "10"));
        long seed = Long.parseLong(opts.getOrDefault("seed", "42"));
        Path rules = Path.of(opts.getOrDefault("rules", "nsai-gate-demo/src/main/resources/nsai-rules.yaml"));
        Path out = Path.of(opts.getOrDefault("out", "build/eval/results.csv"));

        Sampler sampler = new OllamaSampler(
                opts.getOrDefault("ollama", "http://localhost:11434"),
                Duration.ofSeconds(Long.parseLong(opts.getOrDefault("timeout-seconds", "300"))),
                Double.parseDouble(opts.getOrDefault("temperature", "0.7")),
                Boolean.parseBoolean(opts.getOrDefault("json-mode", "true")));

        RuleSet ruleSet;
        try (InputStream in = Files.newInputStream(rules)) {
            ruleSet = RuleSetLoader.load(in, rules.toString());
        }
        GateCheck gate = new GateCheck(new LocalRuleConnector(new RuleEvaluator(ruleSet)), INTENT);

        List<Prompt> prompts = loadPrompts();
        System.out.printf("Evaluating %d model(s) x %d prompts x %d runs = %d calls%n",
                models.size(), prompts.size(), runs, models.size() * prompts.size() * runs);

        List<Row> rows = run(sampler, gate, new PolicyOracle(), models, prompts, runs, seed, System.out);
        writeCsv(rows, out);
        Summary.print(rows, System.out);
        System.out.println("Results written to " + out.toAbsolutePath());
    }

    static List<Row> run(Sampler sampler, GateCheck gate, PolicyOracle oracle, List<String> models,
                         List<Prompt> prompts, int runs, long baseSeed, PrintStream log) throws InterruptedException {
        List<Row> rows = new ArrayList<>();
        for (String model : models) {
            for (Prompt prompt : prompts) {
                for (int run = 1; run <= runs; run++) {
                    long seed = baseSeed + run;
                    long start = System.nanoTime();
                    String output = null;
                    String error = null;
                    try {
                        output = sampler.generate(model, PROMPT_TEMPLATE.formatted(prompt.request()), seed);
                    } catch (IOException e) {
                        error = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
                    }
                    long latency = (System.nanoTime() - start) / 1_000_000;
                    if (output == null) {
                        rows.add(new Row(model, prompt, run, seed, latency, null, error, null, null, null));
                        log.printf("  %s %s run %d: ERROR %s%n", model, prompt.id(), run, error);
                        continue;
                    }
                    GateCheck.Outcome outcome = gate.check(output);
                    PolicyOracle.Verdict after = outcome.soft() == GateCheck.Verdict.REPAIRED
                            ? oracle.check(outcome.softOutput()) : null;
                    rows.add(new Row(model, prompt, run, seed, latency, output, null,
                            oracle.check(output), outcome, after));
                    log.printf("  %s %s run %d: gate=%s/%s (%d ms)%n",
                            model, prompt.id(), run, outcome.hard(), outcome.soft(), latency);
                }
            }
        }
        return rows;
    }

    static List<Prompt> loadPrompts() throws IOException {
        List<Prompt> prompts = new ArrayList<>();
        try (InputStream in = EvalHarness.class.getResourceAsStream("/eval-prompts.tsv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     Objects.requireNonNull(in, "eval-prompts.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", 3);
                if (parts.length != 3) {
                    throw new IllegalStateException("Bad prompt line: " + line);
                }
                prompts.add(new Prompt(parts[0].trim(), parts[1].trim(), parts[2].trim()));
            }
        }
        return prompts;
    }

    static void writeCsv(List<Row> rows, Path out) throws IOException {
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write(String.join(",", "model", "prompt_id", "category", "run", "seed", "latency_ms",
                    "error", "oracle_json", "oracle_compliant", "oracle_violations", "message_rate_mismatch",
                    "gate_hard", "gate_soft", "gate_rule_ids", "repair_changes",
                    "oracle_after_repair_compliant", "oracle_after_repair_violations", "output") + "\n");
            for (Row r : rows) {
                List<String> cells = new ArrayList<>(List.of(r.model(), r.prompt().id(), r.prompt().category(),
                        String.valueOf(r.run()), String.valueOf(r.seed()), String.valueOf(r.latencyMs()),
                        nz(r.error())));
                if (r.oracle() == null) {
                    cells.addAll(List.of("", "", "", "", "", "", "", "", "", "", ""));
                } else {
                    cells.add(String.valueOf(r.oracle().json()));
                    cells.add(String.valueOf(r.oracle().compliant()));
                    cells.add(String.join(";", r.oracle().violations()));
                    cells.add(String.valueOf(r.oracle().messageRateMismatch()));
                    cells.add(r.gate().hard().name());
                    cells.add(r.gate().soft().name());
                    cells.add(String.join(";", r.gate().ruleIds()));
                    cells.add(String.join(";", r.gate().changes()));
                    cells.add(r.oracleAfterRepair() == null ? "" : String.valueOf(r.oracleAfterRepair().compliant()));
                    cells.add(r.oracleAfterRepair() == null ? "" : String.join(";", r.oracleAfterRepair().violations()));
                    cells.add(nz(r.output()));
                }
                w.write(String.join(",", cells.stream().map(EvalHarness::csv).toList()) + "\n");
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value pairs, got: " + a);
            }
            opts.put(a.substring(2), args[++i]);
        }
        return opts;
    }
}
