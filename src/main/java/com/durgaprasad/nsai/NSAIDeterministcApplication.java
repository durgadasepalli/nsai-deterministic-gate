package com.durgaprasad.nsai;

import com.durgaprasad.nsai.service.DemoAiService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class NSAIDeterministcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NSAIDeterministcApplication.class, args);
    }

    @Bean
    public CommandLineRunner runDemo(DemoAiService aiService) {
        return args -> {
            System.out.println("\n--- STARTING NEURO-SYMBOLIC VALIDATION TEST ---");
            
            // This will trigger the @NSDeterministicGate in your DemoAiService
            String result = aiService.simulateAiResponse("Requesting high interest loan...");
            
            System.out.println("FINAL SYSTEM OUTPUT: " + result);
            System.out.println("--- TEST COMPLETE ---\n");
        };
    }
}