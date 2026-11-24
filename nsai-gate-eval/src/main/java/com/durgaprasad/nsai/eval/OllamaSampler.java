package com.durgaprasad.nsai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls Ollama's {@code /api/generate} with a temperature and seed, so repeated runs
 * sample different answers but the whole study can be replayed with the same seeds.
 * (The demo's client fixes temperature at 0, which is right for a demo but would give
 * the same answer on every run.)
 */
final class OllamaSampler implements Sampler {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI uri;
    private final Duration timeout;
    private final double temperature;
    private final boolean jsonMode;

    OllamaSampler(String baseUrl, Duration timeout, double temperature, boolean jsonMode) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.uri = URI.create(base + "/api/generate");
        this.timeout = timeout;
        this.temperature = temperature;
        this.jsonMode = jsonMode;
    }

    @Override
    public String generate(String model, String prompt, long seed) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", false);
        if (jsonMode) {
            body.put("format", "json");
        }
        body.putObject("options").put("temperature", temperature).put("seed", seed);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode text = mapper.readTree(response.body()).get("response");
        if (text == null || !text.isTextual()) {
            throw new IOException("Ollama response has no 'response' field");
        }
        return text.asText();
    }
}
