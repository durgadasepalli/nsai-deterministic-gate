package com.durgaprasad.nsai.core;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class MockRdbmsConnector implements SymbolicConnector {
    @Override
    public boolean validate(String intent, String neuralProposal, Map<String, Object> context) {
        // Mock Logic: If the LLM mentions an interest rate > 10, block it.
        return !neuralProposal.contains("25%"); 
    }

    @Override
    public String getSourceIdentifier() {
        return "DEFAULT_RDBMS";
    }
}