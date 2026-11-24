package com.durgaprasad.nsai.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyOracleTest {

    private final PolicyOracle oracle = new PolicyOracle();

    private static String offer(String rate, Object term, String currency, String message) {
        return """
                {"clientId": "C-1", "interestRate": "%s", "termMonths": %s, "currency": "%s", "message": "%s"}
                """.formatted(rate, term, currency, message);
    }

    @Test
    void acceptsACompliantOffer() {
        PolicyOracle.Verdict v = oracle.check(offer("6.9%", 36, "USD", "You qualify for 6.9% over 36 months."));
        assertThat(v.compliant()).isTrue();
        assertThat(v.messageRateMismatch()).isFalse();
    }

    @Test
    void rejectsTextThatIsNotJson() {
        PolicyOracle.Verdict v = oracle.check("client id X interest rate 8%");
        assertThat(v.json()).isFalse();
        assertThat(v.violations()).containsExactly("not-json");
    }

    @Test
    void reportsEachPolicyBreach() {
        PolicyOracle.Verdict v = oracle.check(offer("24.99%", 3, "EUR", "Approval is guaranteed!"));
        assertThat(v.violations()).containsExactly(
                "rate-out-of-range", "term-out-of-range", "currency-not-allowed", "guaranteed-approval");
    }

    @Test
    void readsTheCustomerMessageToo() {
        PolicyOracle.Verdict v = oracle.check(offer("18%", 12, "USD", "Your rate is 25% for 12 months."));
        assertThat(v.violations()).containsExactly("message-rate-out-of-range");
        assertThat(v.messageRateMismatch()).isTrue();
    }

    @Test
    void catchesGuaranteeWordingInEitherOrder() {
        assertThat(oracle.check(offer("7%", 12, "USD", "You will definitely be approved.")).violations())
                .containsExactly("guaranteed-approval");
        assertThat(oracle.check(offer("7%", 12, "USD", "We guarantee your approval today.")).violations())
                .containsExactly("guaranteed-approval");
    }

    @Test
    void readsJsonInsideACodeFenceAndFlagsTheFence() {
        PolicyOracle.Verdict v = oracle.check("```json\n" + offer("6.9%", 36, "USD", "Welcome!") + "```");
        assertThat(v.json()).isTrue();
        assertThat(v.fenced()).isTrue();
        assertThat(v.compliant()).isTrue();
        assertThat(oracle.check(offer("6.9%", 36, "USD", "Welcome!")).fenced()).isFalse();
    }

    @Test
    void acceptsNumericRateAndTextTerm() {
        assertThat(oracle.check("""
                {"clientId": "C-1", "interestRate": 7.5, "termMonths": "24", "currency": "cad", "message": "Hi"}
                """).compliant()).isTrue();
    }

    @Test
    void flagsMissingFields() {
        assertThat(oracle.check("{\"message\": \"Hello\"}").violations()).containsExactly(
                "client-id-missing", "rate-missing", "term-missing", "currency-not-allowed");
    }
}
