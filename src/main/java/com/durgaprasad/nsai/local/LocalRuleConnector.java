package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.core.SymbolicConnector;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Symbolic connector backed by a local YAML rule file. Needs no database or cloud
 * account, so the gate can be tried and tested anywhere.
 * <p>
 * Use it with {@code @NSDeterministicGate(symbolicSource = "LOCAL_RULES")}.
 */
public class LocalRuleConnector implements SymbolicConnector {

    public static final String SOURCE_ID = "LOCAL_RULES";

    private static final Logger log = LoggerFactory.getLogger(LocalRuleConnector.class);

    private final RuleEvaluator evaluator;

    public LocalRuleConnector(RuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public boolean validate(String intent, String neuralProposal, Map<String, Object> context) {
        return evaluate(intent, neuralProposal, context).valid();
    }

    @Override
    public ValidationResult evaluate(String intent, String neuralProposal, Map<String, Object> context) {
        return evaluator.evaluate(intent, neuralProposal);
    }

    @Override
    public String getSourceIdentifier() {
        return SOURCE_ID;
    }

    @Override
    public void logDiagnosticData(String neuralProposal, String failureReason) {
        log.info("[LocalRules] Proposal failed deterministic rules: {}", failureReason);
    }
}
