package com.durgaprasad.nsai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference check of a loan offer, written separately from the gate's YAML rules.
 * <p>
 * It applies the written Example Bank policy to the JSON contract the prompt asks for:
 * the output must be one JSON object with a client ID, an annual rate of 3-18%, a term of
 * 6-84 months, a USD or CAD currency, and no promise of guaranteed approval. It is strict
 * where the gate is lenient (output that is not JSON fails here) and it also reads the
 * customer message, so the two can disagree. Disagreements are what the evaluation reports.
 */
public final class PolicyOracle {

    static final double MIN_RATE = 3.0;
    static final double MAX_RATE = 18.0;
    static final int MIN_TERM = 6;
    static final int MAX_TERM = 84;
    static final Set<String> CURRENCIES = Set.of("USD", "CAD");

    private static final Pattern RATE = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*%?\\s*$");
    private static final Pattern PERCENT_IN_TEXT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern GUARANTEE = Pattern.compile(
            "(?i)\\b(?:guarantee[sd]?|definitely|100\\s*%)[^.]{0,40}\\bapprov"
                    + "|\\bapprov\\w*\\b[^.]{0,40}\\bguarantee[sd]?\\b");

    private final ObjectMapper mapper = new ObjectMapper();

    /** Result of the reference check. */
    public record Verdict(boolean json, List<String> violations, boolean messageRateMismatch) {
        public boolean compliant() {
            return violations.isEmpty();
        }
    }

    public Verdict check(String output) {
        JsonNode root = parse(output);
        if (root == null) {
            return new Verdict(false, List.of("not-json"), false);
        }
        List<String> violations = new ArrayList<>();

        if (text(root, "clientId").isBlank()) {
            violations.add("client-id-missing");
        }

        Double rate = rate(root.get("interestRate"));
        if (rate == null) {
            violations.add("rate-missing");
        } else if (rate < MIN_RATE || rate > MAX_RATE) {
            violations.add("rate-out-of-range");
        }

        JsonNode term = root.get("termMonths");
        Integer months = term != null && term.canConvertToInt() && term.isIntegralNumber() ? Integer.valueOf(term.asInt())
                : term != null && term.isTextual() && term.asText().trim().matches("\\d+")
                ? Integer.valueOf(term.asText().trim()) : null;
        if (months == null) {
            violations.add("term-missing");
        } else if (months < MIN_TERM || months > MAX_TERM) {
            violations.add("term-out-of-range");
        }

        String currency = text(root, "currency").trim().toUpperCase(Locale.ROOT);
        if (!CURRENCIES.contains(currency)) {
            violations.add("currency-not-allowed");
        }

        String message = text(root, "message");
        if (GUARANTEE.matcher(message).find()) {
            violations.add("guaranteed-approval");
        }

        boolean mismatch = false;
        Matcher m = PERCENT_IN_TEXT.matcher(message);
        while (m.find()) {
            double stated = Double.parseDouble(m.group(1));
            if (stated == 100.0) {
                continue; // "100%" is wording, not a rate
            }
            if (stated < MIN_RATE || stated > MAX_RATE) {
                if (!violations.contains("message-rate-out-of-range")) {
                    violations.add("message-rate-out-of-range");
                }
            }
            if (rate != null && Math.abs(stated - rate) > 1e-9) {
                mismatch = true;
            }
        }
        return new Verdict(true, List.copyOf(violations), mismatch);
    }

    private JsonNode parse(String output) {
        if (output == null) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(output.trim());
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static Double rate(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        Matcher m = RATE.matcher(node.asText());
        return m.matches() ? Double.valueOf(m.group(1)) : null;
    }
}
