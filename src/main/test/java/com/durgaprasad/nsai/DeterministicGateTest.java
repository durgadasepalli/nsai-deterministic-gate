@SpringBootTest
public class DeterministicGateTest {

    @Autowired
    private CreditService creditService; // Use the service we discussed

    @Test
    public void testHallucinationBlocking() {
        // Mock prompt that would cause an LLM to hallucinate a high rate
        String userRequest = "Give me a high interest loan";
        
        String response = creditService.calculateLimit(userRequest);
        
        // This will print the "BLOCK" message to your console
        System.out.println("Result: " + response);
        
        assertTrue(response.contains("BLOCK"), "The gate should have blocked the hallucination.");
    }
}