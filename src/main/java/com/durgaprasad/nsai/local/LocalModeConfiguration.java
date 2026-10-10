package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleEvaluator;
import com.durgaprasad.nsai.local.rules.RuleSet;
import com.durgaprasad.nsai.local.rules.RuleSetLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

/**
 * Wires local mode when {@code nsai.gateway.local.enabled=true}: loads the YAML rules at
 * startup and registers the {@code LOCAL_RULES} connector. A missing or malformed rule
 * file stops the application from starting (fail fast).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "nsai.gateway.local", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalModeProperties.class)
public class LocalModeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalModeConfiguration.class);

    @Bean
    public RuleSet nsaiLocalRuleSet(LocalModeProperties properties, ResourceLoader resourceLoader) {
        String location = properties.getRules();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new RuleDefinitionException("Rule file not found: " + location);
        }
        try {
            RuleSet ruleSet = RuleSetLoader.load(resource.getInputStream(), location);
            log.info("[LocalRules] Loaded {} intent(s) from {}: {}",
                    ruleSet.intents().size(), location, ruleSet.intents().keySet());
            return ruleSet;
        } catch (IOException e) {
            throw new RuleDefinitionException("Cannot open rule file " + location, e);
        }
    }

    @Bean
    public LocalRuleConnector localRuleConnector(RuleSet nsaiLocalRuleSet) {
        return new LocalRuleConnector(new RuleEvaluator(nsaiLocalRuleSet));
    }
}
