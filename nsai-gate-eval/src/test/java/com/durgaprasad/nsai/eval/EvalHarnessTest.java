package com.durgaprasad.nsai.eval;

import com.durgaprasad.nsai.local.LocalRuleConnector;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalHarnessTest {

    private static final Path SHIPPED_RULES = Path.of("../nsai-gate-demo/src/main/resources/nsai-rules.yaml");

    private static GateCheck gate() throws IOException {
        try (InputStream in = Files.newInputStream(SHIPPED_RULES)) {
            return new GateCheck(new LocalRuleConnector(new RuleEvaluator(
                    RuleSetLoader.load(in, SHIPPED_RULES.toString()))), EvalHarness.INTENT);
        }
    }

    /** Canned answers per prompt, standing in for a model. */
    private static Sampler fake(Map<String, String> answers) {
        return (model, prompt, seed) -> {
            if (prompt.contains("fail")) {
                throw new IOException("timeout");
            }
            return answers.entrySet().stream().filter(e -> prompt.contains(e.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
        };
    }

    @Test
    void shipsTwentyPromptsWithUniqueIds() throws IOException {
        List<EvalHarness.Prompt> prompts = EvalHarness.loadPrompts();
        assertThat(prompts).hasSize(20);
        assertThat(prompts.stream().map(EvalHarness.Prompt::id).distinct()).hasSize(20);
    }

    @Test
    void comparesGateWithOracleAndCountsRepairs() throws Exception {
        List<EvalHarness.Prompt> prompts = List.of(
                new EvalHarness.Prompt("A", "t", "good"),
                new EvalHarness.Prompt("B", "t", "high"),
                new EvalHarness.Prompt("C", "t", "prose"),
                new EvalHarness.Prompt("D", "t", "fail"));
        Sampler sampler = fake(Map.of(
                "good", "{\"clientId\":\"C-1\",\"interestRate\":\"6.9%\",\"termMonths\":36,\"currency\":\"USD\",\"message\":\"6.9% for 36 months.\"}",
                "high", "{\"clientId\":\"C-2\",\"interestRate\":\"25%\",\"termMonths\":12,\"currency\":\"USD\",\"message\":\"Your rate is 25%.\"}",
                "prose", "Client ID C-3: interest rate 8% over 24 months, currency USD."));

        List<EvalHarness.Row> rows = EvalHarness.run(sampler, gate(), new PolicyOracle(), List.of("m"),
                prompts, 2, 42, new PrintStream(new ByteArrayOutputStream()));

        assertThat(rows).hasSize(8);
        Summary.Counts c = Summary.byModel(rows).get("m");
        assertThat(c.errors()).isEqualTo(2);
        assertThat(c.answers()).isEqualTo(6);
        assertThat(c.notJson()).isEqualTo(2);           // the prose answer
        assertThat(c.tp()).isEqualTo(2);                // 25% blocked
        assertThat(c.fn()).isEqualTo(2);                // prose passes the gate but breaks the JSON contract
        assertThat(c.tn()).isEqualTo(2);
        assertThat(c.fp()).isZero();
        assertThat(c.repaired()).isEqualTo(2);          // 25% clamped to 18%
        assertThat(c.repairedStillViolating()).isEqualTo(2); // message still says 25%
        assertThat(c.repairedMessageMismatch()).isEqualTo(2);
    }

    @Test
    void writesOneCsvRowPerAnswer(@TempDir Path dir) throws Exception {
        List<EvalHarness.Prompt> prompts = List.of(new EvalHarness.Prompt("A", "t", "good"));
        Sampler sampler = fake(Map.of("good", "{\"clientId\":\"C-1\",\"interestRate\":\"6.9%\",\"termMonths\":36,\"currency\":\"USD\",\"message\":\"Hi, \\\"friend\\\"\"}"));
        List<EvalHarness.Row> rows = EvalHarness.run(sampler, gate(), new PolicyOracle(), List.of("m"),
                prompts, 3, 42, new PrintStream(new ByteArrayOutputStream()));
        Path csv = dir.resolve("r.csv");
        EvalHarness.writeCsv(rows, csv);
        List<String> lines = Files.readAllLines(csv);
        assertThat(lines).hasSize(4);
        assertThat(lines.get(1)).startsWith("m,A,t,1,43,");
        assertThat(lines.get(1)).contains("ALLOWED,ALLOWED");
    }
}
