package com.durgaprasad.nsai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Supplies policy constraints for an intent, cached in the Caffeine L1 cache ('graphPolicies').
 * <p>
 * <b>Status:</b> placeholder. It returns a static constraint list; the Neo4j traversal
 * ({@code MATCH (n:Intent {id: $intent})-[:REQUIRES*1..5]->(c:Constraint) RETURN c.rule})
 * is not wired in yet.
 */
@Component
public class Neo4jGraphConnector {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphConnector.class);

    @Cacheable(value = "graphPolicies", key = "#intent", unless = "#result == null || #result.isEmpty()")
    public List<String> fetchGraphPolicyConstraints(String intent) {
        log.debug("[Neo4j-Context] Loading policy constraints for intent={} (cache miss)", intent);
        return List.of("creditLimit <= 50000", "requiredFields:['clientId', 'monthlyIncome']");
    }
}
