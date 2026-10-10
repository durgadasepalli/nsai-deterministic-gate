package com.durgaprasad.nsai.local;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for local mode ({@code nsai.gateway.local.*}): deterministic rules loaded from
 * a YAML file, so the gate runs with no database or cloud account.
 */
@ConfigurationProperties(prefix = "nsai.gateway.local")
public class LocalModeProperties {

    /** Turns local mode on: loads the rule file and registers the {@code LOCAL_RULES} connector. */
    private boolean enabled = false;

    /** Location of the YAML rule file, e.g. {@code classpath:nsai-rules.yaml} or {@code file:/config/rules.yaml}. */
    private String rules = "classpath:nsai-rules.yaml";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }
}
