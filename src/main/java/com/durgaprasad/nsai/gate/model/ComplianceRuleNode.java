package com.durgaprasad.nsai.gate.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.HashSet;
import java.util.Set;

@Node("ComplianceRule")
public class ComplianceRuleNode {

    @Id
    private String ruleId;
    private String description;
    private String targetKey;
    private Double maxValue;
    private boolean criticalTier1;

    // Self-referencing relationship to handle multi-tiered evaluation paths
    @Relationship(type = "DEPENDS_ON", direction = Relationship.Direction.OUTGOING)
    private Set<ComplianceRuleNode> dependentRules = new HashSet<>();

    // Constructors, Getters, Setters omitted for brevity
    public String getRuleId() { return ruleId; }
    public String getTargetKey() { return targetKey; }
    public Double getMaxValue() { return maxValue; }
    public boolean isCriticalTier1() { return criticalTier1; }
    public Set<ComplianceRuleNode> getDependentRules() { return dependentRules; }
}