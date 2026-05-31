package com.durgaprasad.nsai.aspect;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.core.SymbolicConnector;
import com.durgaprasad.nsai.core.Neo4jGraphConnector;
import com.durgaprasad.nsai.diagnostic.MlDiagnosticLayer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * The core Neuro-Symbolic Interceptor.
 * Orchestrates the hand-off between Neural (LLM) and Symbolic (Deterministic) layers.
 */
@Aspect
@Component
public class DeterministicGateAspect {

    @Autowired
    private List<SymbolicConnector> connectors;

    @Autowired
    private Neo4jGraphConnector graphConnector; // Added for v1.1 Knowledge Graph verification

    @Autowired
    private MlDiagnosticLayer diagnosticLayer;   // Added for v1.1 ML Diagnostic feedback loops

    @Around("@annotation(gateConfig)")
    public Object validateAIPipeline(ProceedingJoinPoint joinPoint, NSDeterministicGate gateConfig) throws Throwable {
        System.out.println("\n[NSAI-Gate] Intercepting incoming probabilistic unstructured LLM proposal payload...");
        
        // 1. Execute the Neural Layer (Invoke the LLM method)
        Object neuralProposal = joinPoint.proceed();
        
        if (!(neuralProposal instanceof String)) {
            return neuralProposal; // Skip validation if result isn't a String/JSON
        }

        String proposalText = (String) neuralProposal;

        // 2. Locate the requested Symbolic Source
        SymbolicConnector connector = connectors.stream()
            .filter(c -> c.getSourceIdentifier().equals(gateConfig.symbolicSource()))
            .findFirst()
            .orElse(null);

        if (connector != null) {
            Map<String, Object> context = new HashMap<>(); 
            context.put("origin", "NSAI-GATEWAY");

            // 3. Perform Deterministic Validation
            boolean isValid = connector.validate(gateConfig.intent(), proposalText, context);

            if (!isValid) {
                System.out.println("[NSAI-Gate] Base Schema Validation: FAILED. Output contains structural anomalies.");
                
                // Check if ML Diagnostics are enabled via annotation config
                if (gateConfig.enableMLDiagnostics()) {
                    connector.logDiagnosticData(proposalText, "LOGIC_VIOLATION");
                    
                    // v1.1 Step: Fetch structural constraints from Knowledge Graph
                    List<String> graphConstraints = graphConnector.fetchGraphPolicyConstraints(gateConfig.intent());
                    
                    // v1.1 Step: Fire the Machine Learning Diagnostic Self-Correcting Loop
                    Map<String, Object> repairedPayload = diagnosticLayer.executeCorrectionDeltaLoop(proposalText, graphConstraints);
                    
                    System.out.println("[NSAI-Gate] Injecting corrected deterministic data back into application execution flow.");
                    return "ACCEPTED WITH ML DIAGNOSTIC REPAIR: " + repairedPayload.toString();
                }
                
                return "BLOCK: Neural proposal violated deterministic symbolic logic for " + gateConfig.intent();
            }
        }

        return neuralProposal;
    }
}