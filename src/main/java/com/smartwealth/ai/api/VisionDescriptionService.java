package com.smartwealth.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class VisionDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(VisionDescriptionService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public VisionDescriptionService() {
        this.restTemplate = new RestTemplate();
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
        this.objectMapper = new ObjectMapper();
    }

    public String describeImage(byte[] imageBytes, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DASHSCOPE_API_KEY not set, skipping AI description");
            return "";
        }

        try {
            String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> systemMsg = Map.of("role", "system", "content", List.of(Map.of("text", "You are a product photographer. Describe the item in one sentence under 80 chars — its style, shape, material, color, and key features. Output only the description, no prefix.")));
            Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(Map.of("image", base64Image)));

            Map<String, Object> requestBody = Map.of(
                    "model", "qwen-vl-plus",
                    "input", Map.of("messages", List.of(systemMsg, userMsg)),
                    "parameters", Map.of("max_tokens", 150, "temperature", 0.3));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Vision API error: {} — body: {}", response.getStatusCode(), response.getBody());
                return "";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode msg = choices.get(0).path("message");
                JsonNode content = msg.path("content");
                // Content may be array or string depending on model response format
                if (content.isArray() && !content.isEmpty()) {
                    return content.get(0).path("text").asText("").trim();
                } else {
                    return content.asText("").trim();
                }
            }
            return "";
        } catch (Exception e) {
            log.warn("Failed to auto-describe image via vision model", e);
            return "";
        }
    }
}
