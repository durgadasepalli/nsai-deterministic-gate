package com.durgaprasad.nsai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class MockRdbmsConnector implements SymbolicConnector {

    private static final Logger log = LoggerFactory.getLogger(MockRdbmsConnector.class);
    
    @Override
    public boolean validate(String intent, String neuralProposal, Map<String, Object> context) {
        // If the LLM mentions an interest rate of 25%, it violates our standard policy max limit
        return !neuralProposal.contains("25%"); 
    }

    @Override
    public ValidationResult evaluate(String intent, String neuralProposal, Map<String, Object> context) {
        return validate(intent, neuralProposal, context)
                ? ValidationResult.pass()
                : ValidationResult.fail("mock-rate-cap",
                        "Proposal mentions a 25% interest rate, above the policy maximum");
    }

    @Override
    public String getSourceIdentifier() {
        return "DEFAULT_RDBMS";
    }

    @Override
    public void logDiagnosticData(String neuralProposal, String failureReason) {
        log.info("[MockRdbmsConnector] Policy violation classification: {}", failureReason);
    }
}