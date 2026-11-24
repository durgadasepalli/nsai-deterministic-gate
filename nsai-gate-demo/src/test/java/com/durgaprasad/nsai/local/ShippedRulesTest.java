package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.core.ValidationResult;
import com.durgaprasad.nsai.core.Violation;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleSet;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShippedRulesTest {

    private static RuleSet shipped() {
        InputStream in = ShippedRulesTest.class.getResourceAsStream("/nsai-rules.yaml");
        assertThat(in).isNotNull();
        return RuleSetLoader.load(in, "nsai-rules.yaml");
    }

    private static List<String> violations(String message) {
        String offer = "{\"clientId\":\"C-1\",\"interestRate\":\"7.5%\",\"termMonths\":36,"
                + "\"currency\":\"USD\",\"message\":\"" + message + "\"}";
        ValidationResult result = new RuleEvaluator(shipped()).evaluate("LOAN_OFFER", offer);
        return result.violations().stream().map(Violation::ruleId).toList();
    }

    @Test
    @DisplayName("The rule file shipped with the demo is valid")
    void shippedRuleFileLoads() {
        assertThat(shipped().forIntent("LOAN_OFFER")).isPresent();
    }

    /** Wordings taken from local model answers in the evaluation harness. */
    @ParameterizedTest
    @ValueSource(strings = {
            "Great news! You have guaranteed approval at a low rate.",
            "Congratulations! Approval guaranteed for $4,000 over 12 months.",
            "You will definitely be approved for the loan of $9,000 over 36 months.",
            "You will be definitely approved for the loan.",
            "We guarantee your approval today."})
    void promisesOfApprovalAreForbidden(String message) {
        assertThat(violations(message)).containsExactly("no-guaranteed-approval");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Your offer is ready: 7.5% over 36 months.",
            "Approval depends on a standard credit check.",
            "We are happy to help with your home repairs."})
    void ordinaryOfferWordingIsAllowed(String message) {
        assertThat(violations(message)).isEmpty();
    }

    @Test
    @DisplayName("A null client ID is a missing client ID")
    void nullClientIdIsRejected() {
        ValidationResult result = new RuleEvaluator(shipped()).evaluate("LOAN_OFFER",
                "{\"clientId\":null,\"interestRate\":\"7.5%\",\"termMonths\":12,\"currency\":\"USD\","
                        + "\"message\":\"Congratulations, your offer is ready.\"}");
        assertThat(result.violations()).extracting(Violation::ruleId).containsExactly("client-id-required");
    }
}
