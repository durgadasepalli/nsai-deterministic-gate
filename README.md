# nsai-deterministic-gate: A Self-Optimizing Neuro-Symbolic Gateway
Eliminating the "Hallucination Tax" via Deterministic Logic and ML-Driven Diagnostics.
nsai-deterministic-gate is a specialized Spring Boot framework designed for Senior Architects implementing Generative AI in mission-critical environments (Finance, Healthcare, Cloud Infrastructure). It enforces a Neuro-Symbolic (NSAI) architecture—separating "Perception" (LLMs) from "Truth" (Symbolic Logic)—while utilizing Machine Learning to classify and optimize the interaction between these layers.

## The Architecture: NSAI + ML
This framework implements a three-tier governance model:

**Neural Layer (Inference):** Vertex AI or Amazon Bedrock handles intent extraction and conversational logic.

**Symbolic Layer (Enforcement):** GCP Enterprise Knowledge Graph or AWS Neptune enforces deterministic business rules via Google Cloud Workflows.

**ML Diagnostic Layer (Optimization):** A classification model analyzes every "blocked" proposal to identify patterns of Logic Drift and provide an auditable report on Hallucination Risk.

## Key Features
**@NSDeterministicGate Interceptor:** A Spring AOP-based annotation to wrap AI services in a deterministic safety layer.

**Symbolic Connectors:** Native integration with Graph Databases to serve as a Single Source of Truth (SSOT).

**ML-Feedback Module:** An integrated Machine Learning classifier that categorizes "Hallucinations" (e.g., Policy Violation vs. Calculation Error) to refine system prompts automatically.

**Cloud Orchestration Adapters:** Ready-to-use templates for Google Cloud Workflows and AWS Step Functions.

## Getting Started
**Maven Dependency**
```xml
<dependency>
    <groupId>com.durgaprasad.ai</groupId>
    <artifactId>nsai-deterministic-gate-starter</artifactId>
    <version>1.0.0-BETA</version>
</dependency>

**Example Usage**
```java
@Service
public class FinancialService {

    @NSDeterministicGate(
        intent = "LOAN_ELIGIBILITY",
        symbolicSource = "GCP_KNOWLEDGE_GRAPH",
        enableMLDiagnostics = true
    )
    public String checkEligibility(String userPrompt) {
        // The gate intercepts this call and validates the response 
        // against the Knowledge Graph before returning to the user.
        return vertexAiClient.prompt(userPrompt);
    }
}

