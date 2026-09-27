package com.durgaprasad.nsai.core;

import com.durgaprasad.nsai.gate.config.CacheConfig;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the v1.3 L1 Caffeine cache on the real Spring proxy (not a mock), using
 * cache statistics rather than wall-clock timing alone, so the test is stable on CI.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, Neo4jGraphConnector.class})
class Neo4jGraphConnectorCacheTest {

    @Autowired
    private Neo4jGraphConnector graphConnector;

    @Autowired
    private CacheManager cacheManager;

    private CaffeineCache cache;

    @BeforeEach
    void clearCache() {
        cache = (CaffeineCache) cacheManager.getCache("graphPolicies");
        assertThat(cache).isNotNull();
        cache.clear();
    }

    private CacheStats stats() {
        return cache.getNativeCache().stats();
    }

    @Test
    @DisplayName("Second lookup for an intent is served from the L1 cache")
    void secondLookupIsACacheHit() {
        CacheStats before = stats();

        long startMiss = System.nanoTime();
        List<String> first = graphConnector.fetchGraphPolicyConstraints("LOAN_OFFER");
        long missMicros = (System.nanoTime() - startMiss) / 1_000;

        long startHit = System.nanoTime();
        List<String> second = graphConnector.fetchGraphPolicyConstraints("LOAN_OFFER");
        long hitMicros = (System.nanoTime() - startHit) / 1_000;

        CacheStats delta = stats().minus(before);

        System.out.printf("%n=== NSAI v1.3 L1 cache trace: miss %d µs, hit %d µs ===%n", missMicros, hitMicros);

        assertThat(second).isEqualTo(first);
        assertThat(delta.missCount()).isEqualTo(1);
        assertThat(delta.hitCount()).isEqualTo(1);
        // The miss includes the simulated 25 ms graph traversal; the hit is in-memory.
        assertThat(missMicros).isGreaterThanOrEqualTo(25_000);
        assertThat(hitMicros).isLessThan(missMicros);
    }

    @Test
    void differentIntentsAreCachedSeparately() {
        graphConnector.fetchGraphPolicyConstraints("LOAN_OFFER");
        graphConnector.fetchGraphPolicyConstraints("CLAIM_CHECK");

        assertThat(cache.get("LOAN_OFFER")).isNotNull();
        assertThat(cache.get("CLAIM_CHECK")).isNotNull();
        assertThat(cache.getNativeCache().estimatedSize()).isEqualTo(2);
    }
}
