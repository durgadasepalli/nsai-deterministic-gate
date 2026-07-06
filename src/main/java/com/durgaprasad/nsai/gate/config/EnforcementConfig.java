package com.durgaprasad.nsai.gate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "nsai.gateway.enforcement")
public class EnforcementConfig {
    
    private EnforcementMode mode = EnforcementMode.SOFT_REPAIR; // Default fallback

    public EnforcementMode getMode() {
        return mode;
    }

    public void setMode(EnforcementMode mode) {
        this.mode = mode;
    }
}