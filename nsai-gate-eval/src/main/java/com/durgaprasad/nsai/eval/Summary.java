package com.durgaprasad.nsai.eval;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prints per-model counts. The oracle is the reference: a "miss" is an answer the oracle
 * rejects but the gate allowed; a "false block" is an answer the oracle accepts but the
 * gate blocked. Every count can be recomputed from the CSV.
 */
final class Summary {

    record Counts(int answers, int errors, int notJson, int fenced, int oracleViolating,
                  int tp, int fn, int fp, int tn,
                  int repaired, int repairedStillViolating, int repairedMessageMismatch, long medianLatencyMs) { }

    static Map<String, Counts> byModel(List<EvalHarness.Row> rows) {
        Set<String> models = new LinkedHashSet<>();
        rows.forEach(r -> models.add(r.model()));
        Map<String, Counts> result = new LinkedHashMap<>();
        for (String model : models) {
            int answers = 0, errors = 0, notJson = 0, fenced = 0, violating = 0, tp = 0, fn = 0, fp = 0, tn = 0;
            int repaired = 0, stillViolating = 0, mismatch = 0;
            List<Long> latencies = new ArrayList<>();
            for (EvalHarness.Row r : rows) {
                if (!r.model().equals(model)) {
                    continue;
                }
                latencies.add(r.latencyMs());
                if (r.oracle() == null) {
                    errors++;
                    continue;
                }
                answers++;
                if (!r.oracle().json()) {
                    notJson++;
                }
                if (r.oracle().fenced()) {
                    fenced++;
                }
                boolean bad = !r.oracle().compliant();
                boolean blocked = r.gate().hard() == GateCheck.Verdict.BLOCKED;
                if (bad) {
                    violating++;
                }
                if (bad && blocked) tp++;
                else if (bad) fn++;
                else if (blocked) fp++;
                else tn++;
                if (r.gate().soft() == GateCheck.Verdict.REPAIRED) {
                    repaired++;
                    if (r.oracleAfterRepair() != null && !r.oracleAfterRepair().compliant()) {
                        stillViolating++;
                    }
                    if (r.oracleAfterRepair() != null && r.oracleAfterRepair().messageRateMismatch()) {
                        mismatch++;
                    }
                }
            }
            latencies.sort(Long::compare);
            long median = latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2);
            result.put(model, new Counts(answers, errors, notJson, fenced, violating, tp, fn, fp, tn,
                    repaired, stillViolating, mismatch, median));
        }
        return result;
    }

    static void print(List<EvalHarness.Row> rows, PrintStream out) {
        out.println();
        out.println("model | answers | errors | not JSON | fenced JSON | oracle violations | gate blocked violations (TP)"
                + " | missed (FN) | false blocks (FP) | clean allowed (TN) | repaired | repaired but oracle still"
                + " rejects | repaired with stale message rate | median ms");
        byModel(rows).forEach((model, c) -> out.printf("%s | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d%n",
                model, c.answers(), c.errors(), c.notJson(), c.fenced(), c.oracleViolating(), c.tp(), c.fn(), c.fp(), c.tn(),
                c.repaired(), c.repairedStillViolating(), c.repairedMessageMismatch(), c.medianLatencyMs()));
    }
}
