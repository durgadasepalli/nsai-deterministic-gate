package com.durgaprasad.nsai.gate.aspect;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import com.durgaprasad.nsai.core.SymbolicConnector;
import com.durgaprasad.nsai.core.Neo4jGraphConnector;
import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.diagnostic.MlDiagnosticLayer;
import com.durgaprasad.nsai.gate.config.EnforcementConfig;
import com.durgaprasad.nsai.gate.config.EnforcementMode;
import com.durgaprasad.nsai.gate.exception.ComplianceViolationException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeterministicGateAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private NSDeterministicGate gateConfig;

    @Mock
    private SymbolicConnector mockConnector;

    @Mock
    private Neo4jGraphConnector graphConnector;

    @Mock
    private MlDiagnosticLayer diagnosticLayer;

    @Spy
    private List<SymbolicConnector> connectors = new ArrayList<>();

    @Mock
    private EnforcementConfig enforcementConfig;

    @InjectMocks
    private DeterministicGateAspect gateAspect;

    private final String sampleIntent = "LOAN_OFFER";
    private final String mockSourceId = "MOCK_RDBMS";
    private final String invalidNeuralProposal = "{\"interestRate\": \"25%\"}";
    private final ValidationResult rateViolation =
            ValidationResult.fail("max-interest-rate", "interestRate = 25 exceeds the maximum 18");

    @BeforeEach
    void setUp() {
        // Prepare the spied list with our mocked rule engine connector
        connectors.clear();
        connectors.add(mockConnector);

        // Setup common stubbing for annotation definitions
        lenient().when(gateConfig.symbolicSource()).thenReturn(mockSourceId);
        lenient().when(gateConfig.intent()).thenReturn(sampleIntent);
        lenient().when(mockConnector.getSourceIdentifier()).thenReturn(mockSourceId);
    }

    @Test
    @DisplayName("Should pass through immediately if neural proposal is valid and compliant")
    void testSuccessfulPassThrough() throws Throwable {
        String validProposal = "{\"interestRate\": \"4.5%\"}";
        when(joinPoint.proceed()).thenReturn(validProposal);
        when(mockConnector.evaluate(eq(sampleIntent), eq(validProposal), anyMap())).thenReturn(ValidationResult.pass());

        Object result = gateAspect.validateAIPipeline(joinPoint, gateConfig);

        assertEquals(validProposal, result);
        verifyNoInteractions(enforcementConfig, graphConnector, diagnosticLayer);
    }

    @Test
    @DisplayName("v1.2 HARD_REJECT: Should throw exception on validation failure, bypassing SLM repair")
    void testHardRejectEnforcement() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn(invalidNeuralProposal);
        when(mockConnector.evaluate(eq(sampleIntent), eq(invalidNeuralProposal), anyMap())).thenReturn(rateViolation);
        when(enforcementConfig.getMode()).thenReturn(EnforcementMode.HARD_REJECT);

        // Act & Assert: the exception carries the failed rule, not just a message
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class,
                () -> gateAspect.validateAIPipeline(joinPoint, gateConfig));
        assertEquals(sampleIntent, ex.getIntent());
        assertEquals(List.of("max-interest-rate"), ex.getViolations().stream().map(Violation::ruleId).toList());
        assertTrue(ex.getMessage().contains("max-interest-rate"));

        // Ensure the exception cut execution short before calling v1.1 diagnostic branches
        verify(enforcementConfig, times(1)).getMode();
        verifyNoInteractions(graphConnector, diagnosticLayer);
    }

    @Test
    @DisplayName("v1.2 SOFT_REPAIR: Should fall back to v1.1 SLM correction loop when diagnostics are enabled")
    void testSoftRepairFallbackWithDiagnosticsEnabled() throws Throwable {
        // Arrange
        Map<String, Object> mockRepairedMap = new HashMap<>();
        mockRepairedMap.put("interestRate", "9.5%");
        mockRepairedMap.put("status", "REPAIRED");

        List<String> mockConstraints = Collections.singletonList("maxRate<=10%");

        when(joinPoint.proceed()).thenReturn(invalidNeuralProposal);
        when(mockConnector.evaluate(eq(sampleIntent), eq(invalidNeuralProposal), anyMap())).thenReturn(rateViolation);
        when(enforcementConfig.getMode()).thenReturn(EnforcementMode.SOFT_REPAIR);
        when(gateConfig.enableMLDiagnostics()).thenReturn(true);
        
        // v1.1 Mocking interactions
        when(graphConnector.fetchGraphPolicyConstraints(sampleIntent)).thenReturn(mockConstraints);
        when(diagnosticLayer.executeCorrectionDeltaLoop(invalidNeuralProposal, mockConstraints)).thenReturn(mockRepairedMap);

        // Act
        Object result = gateAspect.validateAIPipeline(joinPoint, gateConfig);

        // Assert
        assertNotNull(result);
        assertTrue(result.toString().contains("ACCEPTED WITH ML DIAGNOSTIC REPAIR"));
        assertTrue(result.toString().contains("9.5%"));
        
        // Verify v1.1 loop completed as intended
        verify(mockConnector, times(1)).logDiagnosticData(invalidNeuralProposal, "LOGIC_VIOLATION");
        verify(graphConnector, times(1)).fetchGraphPolicyConstraints(sampleIntent);
        verify(diagnosticLayer, times(1)).executeCorrectionDeltaLoop(invalidNeuralProposal, mockConstraints);
    }

    @Test
    @DisplayName("Fails closed when symbolicSource names no registered connector")
    void testFailsClosedOnUnknownSymbolicSource() throws Throwable {
        // Arrange: the annotation points at a source that does not exist (e.g. a typo)
        when(gateConfig.symbolicSource()).thenReturn("TYPO_SOURCE");
        when(joinPoint.proceed()).thenReturn(invalidNeuralProposal);

        // Act & Assert: the gate refuses instead of returning unchecked LLM output
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gateAspect.validateAIPipeline(joinPoint, gateConfig));

        assertTrue(ex.getMessage().contains("TYPO_SOURCE"), "message names the missing source");
        assertTrue(ex.getMessage().contains(mockSourceId), "message lists the registered sources");
        verify(mockConnector, never()).evaluate(anyString(), anyString(), anyMap());
        verifyNoInteractions(enforcementConfig, graphConnector, diagnosticLayer);
    }

    @Test
    @DisplayName("SOFT_REPAIR with diagnostics off returns a BLOCK message listing the failed rules")
    void testBlockMessageWhenDiagnosticsDisabled() throws Throwable {
        when(joinPoint.proceed()).thenReturn(invalidNeuralProposal);
        when(mockConnector.evaluate(eq(sampleIntent), eq(invalidNeuralProposal), anyMap())).thenReturn(rateViolation);
        when(enforcementConfig.getMode()).thenReturn(EnforcementMode.SOFT_REPAIR);
        when(gateConfig.enableMLDiagnostics()).thenReturn(false);

        Object result = gateAspect.validateAIPipeline(joinPoint, gateConfig);

        assertTrue(result.toString().startsWith("BLOCK: Neural proposal violated deterministic symbolic logic for LOAN_OFFER"));
        assertTrue(result.toString().contains("max-interest-rate"));
        verifyNoInteractions(graphConnector, diagnosticLayer);
    }

    @Test
    @DisplayName("Non-text results are returned without validation")
    void testNonStringResultIsNotValidated() throws Throwable {
        Map<String, Object> structured = Map.of("interestRate", 25);
        when(joinPoint.proceed()).thenReturn(structured);

        assertSame(structured, gateAspect.validateAIPipeline(joinPoint, gateConfig));
        verifyNoInteractions(mockConnector, enforcementConfig, graphConnector, diagnosticLayer);
    }
}
