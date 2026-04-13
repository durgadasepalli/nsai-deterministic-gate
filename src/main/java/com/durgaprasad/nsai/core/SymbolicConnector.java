package com.durgaprasad.nsai.core;

import java.util.Map;

/**
 * Core interface for connecting Neural outputs to Symbolic Truth sources.
 * Implementations facilitate deterministic validation via Cloud Orchestrators 
 * like Google Cloud Workflows or AWS Step Functions.
 * * @author Durga Prasad Dasepalli
 */
public interface SymbolicConnector {

    /**
     * Validates a neural proposal against the symbolic layer.
     * * @param intent The business intent being validated.
     * @param neuralProposal The raw output from the LLM.
     * @param context Additional metadata for validation (e.g., user ID, region).
     * @return boolean True if the proposal meets deterministic constraints.
     */
    boolean validate(String intent, String neuralProposal, Map<String, Object> context);

    /**
     * Retrieves the specific symbolic source identifier (e.g., "GCP_KNOWLEDGE_GRAPH").
     */
    String getSourceIdentifier();

    /**
     * Optional: Triggers the ML Diagnostic layer if a validation failure occurs.
     */
    default void logDiagnosticData(String neuralProposal, String failureReason) {
        // Implementation for ML-driven Hallucination Classification
    }
}
