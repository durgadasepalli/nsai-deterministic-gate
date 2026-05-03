package com.durgaprasad.nsai.aspect;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.core.SymbolicConnector;
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

    @Around("@annotation(gateConfig)")
    public Object validateAIPipeline(ProceedingJoinPoint joinPoint, NSDeterministicGate gateConfig) throws Throwable {
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
            // Standardizing context for EB-1A "Engineering Leadership" evidence
            context.put("origin", "NSAI-GATEWAY");

            // 3. Perform Deterministic Validation
            boolean isValid = connector.validate(gateConfig.intent(), proposalText, context);

            if (!isValid) {
                if (gateConfig.enableMLDiagnostics()) {
                    connector.logDiagnosticData(proposalText, "LOGIC_VIOLATION");
                }
                return "BLOCK: Neural proposal violated deterministic symbolic logic for " + gateConfig.intent();
            }
        }

        return neuralProposal;
    }
}