package com.durgaprasad.nsai.gate.repository;

import com.durgaprasad.nsai.gate.model.ComplianceRuleNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ComplianceRuleRepository extends Neo4jRepository<ComplianceRuleNode, String> {

    /**
     * Executes a deep-nested, recursive Cypher trace to pull an entire rule dependency subtree 
     * matching a specific intent path (e.g., LOAN_OFFER).
     */
    @Query("MATCH (r:ComplianceRule {ruleId: $startRuleId}) " +
           "OPTIONAL MATCH p = (r)-[:DEPENDS_ON*1..5]->(dep:ComplianceRule) " +
           "RETURN r, collect(nodes(p)), collect(relationships(p))")
    Optional<ComplianceRuleNode> findRuleTreeWithDependencies(@Param("startRuleId") String startRuleId);
}