package com.durgaprasad.nsai.core;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class MockRdbmsConnector implements SymbolicConnector {
    
    @Override
    public boolean validate(String intent, String neuralProposal, Map<String, Object> context) {
        // If the LLM mentions an interest rate of 25%, it violates our standard policy max limit
        return !neuralProposal.contains("25%"); 
    }

    @Override
    public String getSourceIdentifier() {
        return "DEFAULT_RDBMS";
    }

    @Override
    public void logDiagnosticData(String neuralProposal, String failureReason) {
        System.out.println("[MockRdbmsConnector] Policy Violation Classification: " + failureReason);
    }
}