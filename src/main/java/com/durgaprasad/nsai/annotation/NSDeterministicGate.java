package com.durgaprasad.nsai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for Neuro-Symbolic (NSAI) validation.
 * Intercepts LLM outputs and validates them against a deterministic symbolic layer.
 * * @author Durga Prasad Dasepalli
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NSDeterministicGate {

    /**
     * The business intent for the transaction (e.g., "LOAN_APPROVAL").
     */
    String intent() default "";

    /**
     * The symbolic data source to use for validation (e.g., "GCP_KNOWLEDGE_GRAPH").
     */
    String symbolicSource() default "DEFAULT_RDBMS";

    /**
     * Enables Machine Learning classification to diagnose and log hallucinations.
     */
    boolean enableMLDiagnostics() default true;

    /**
     * Fallback method name if the symbolic layer blocks the neural proposal.
     */
    String fallbackMethod() default "";
}
