package com.durgaprasad.nsai.eval;

import com.durgaprasad.nsai.core.RepairChange;
import com.durgaprasad.nsai.core.RepairResult;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.local.LocalRuleConnector;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies the gate's decision to one model answer in both enforcement modes, using the
 * same connector the Spring aspect calls. HARD_REJECT: allowed or blocked. SOFT_REPAIR:
 * allowed, repaired (clamped and re-checked against every rule) or blocked.
 */
final class GateCheck {

    enum Verdict { ALLOWED, REPAIRED, BLOCKED }

    record Outcome(Verdict hard, Verdict soft, List<String> ruleIds, List<String> changes, String softOutput) { }

    private final LocalRuleConnector connector;
    private final String intent;

    GateCheck(LocalRuleConnector connector, String intent) {
        this.connector = connector;
        this.intent = intent;
    }

    Outcome check(String output) {
        ValidationResult result = connector.evaluate(intent, output, Map.of());
        if (result.valid()) {
            return new Outcome(Verdict.ALLOWED, Verdict.ALLOWED, List.of(), List.of(), output);
        }
        List<String> ruleIds = result.violations().stream().map(Violation::ruleId).distinct().toList();
        Optional<RepairResult> repair = connector.repair(intent, output, result, Map.of());
        if (repair.isPresent()) {
            List<String> changes = repair.get().changes().stream().map(GateCheck::describe).toList();
            return new Outcome(Verdict.BLOCKED, Verdict.REPAIRED, ruleIds, changes, repair.get().repairedProposal());
        }
        return new Outcome(Verdict.BLOCKED, Verdict.BLOCKED, ruleIds, List.of(), null);
    }

    private static String describe(RepairChange c) {
        return c.field() + ":" + c.from() + "->" + c.to();
    }
}
