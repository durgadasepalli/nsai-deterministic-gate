package com.durgaprasad.nsai.service;

import com.durgaprasad.nsai.annotation.NSDeterministicGate;
import org.springframework.stereotype.Service;

@Service
public class DemoAiService {

    @NSDeterministicGate(intent = "LOAN_OFFER", symbolicSource = "DEFAULT_RDBMS")
    public String simulateAiResponse(String input) {
        // This simulates a "Hallucinating" LLM output
        return "Based on your profile, your interest rate is 25%.";
    }
}