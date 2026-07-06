package com.durgaprasad.nsai.gate.aspect;

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

import com.durgaprasad.nsai.gate.config.EnforcementConfig;
import com.durgaprasad.nsai.gate.config.EnforcementMode;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * The core Neuro-Symbolic Interceptor (Evolved for v1.2).
 * Orchestrates the hand-off between Neural (LLM) and Symbolic layers
 * with switchable Hard Reject / Soft Repair enforcement capabilities.
 */
@Aspect
@Component
public class DeterministicGateAspect {

    @Autowired
    private List<SymbolicConnector> connectors;

    @Autowired
    private Neo4jGraphConnector graphConnector; // Retained from v1.1

    @Autowired
    private MlDiagnosticLayer diagnosticLayer;   // Retained from v1.1

    @Autowired
    private EnforcementConfig enforcementConfig; // Added for v1.2 Runtime Governance

    @Around("@annotation(gateConfig)")
    public Object validateAIPipeline(ProceedingJoinPoint joinPoint, NSDeterministicGate gateConfig) throws Throwable {
        System.out.println("\n[NSAI-Gate v1.2] Intercepting incoming probabilistic unstructured LLM proposal payload...");
        
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

            // 3. Perform Deterministic Validation via Core Rule Engine
            boolean isValid = connector.validate(gateConfig.intent(), proposalText, context);

            if (!isValid) {
                System.out.println("[NSAI-Gate v1.2] Base Schema Validation: FAILED. Output contains structural anomalies.");
                
                // Fetch active v1.2 strategy from the central Enforcement Controller
                EnforcementMode activeMode = enforcementConfig.getMode();
                
                if (activeMode == EnforcementMode.HARD_REJECT) {
                    System.out.println("[NSAI-Gate v1.2] CRITICAL: HARD_REJECT mode active. Terminating pipeline execution flow immediately.");
                    throw new ComplianceViolationException("Execution halted: Neural payload violated strict deterministic constraints for intent: " + gateConfig.intent());
                }
                
                // Fallback to v1.1 Soft Repair Path if diagnostics are explicitly enabled
                if (gateConfig.enableMLDiagnostics()) {
                    System.out.println("[NSAI-Gate v1.2] SOFT_REPAIR path engaged. Invoking ML Diagnostic self-correcting loop...");
                    
                    connector.logDiagnosticData(proposalText, "LOGIC_VIOLATION");
                    
                    // Fetch structural constraints from Knowledge Graph (v1.1 Step)
                    List<String> graphConstraints = graphConnector.fetchGraphPolicyConstraints(gateConfig.intent());
                    
                    // Fire the Machine Learning Diagnostic Self-Correcting Loop (v1.1 Step)
                    Map<String, Object> repairedPayload = diagnosticLayer.executeCorrectionDeltaLoop(proposalText, graphConstraints);
                    
                    System.out.println("[NSAI-Gate v1.2] Injecting corrected deterministic data back into application execution flow.");
                    return "ACCEPTED WITH ML DIAGNOSTIC REPAIR: " + repairedPayload.toString();
                }
                
                return "BLOCK: Neural proposal violated deterministic symbolic logic for " + gateConfig.intent();
            }
        }

        return neuralProposal;
    }
}


