package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwealth.ai.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ImageEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ImageEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public ImageEmbeddingService(DemoProperties props) {
        this.restTemplate = new RestTemplate();
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
        this.model = props.getImageSearch().getModel();
        this.objectMapper = new ObjectMapper();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DASHSCOPE_API_KEY env var is not set — multimodal embedding calls will fail");
        }
    }

    /**
     * Generate an embedding vector for the given image bytes.
     */
    public float[] embedImage(byte[] imageBytes, String contentType) {
        String mime = contentType != null ? contentType : "image/jpeg";
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, String> content = Map.of("image", dataUri);
        return callApi(content);
    }

    private float[] callApi(Map<String, String> content) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", Map.of("contents", List.of(content)),
                "parameters", Map.of()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        String url = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Multimodal embedding API error: {} — body: {}", response.getStatusCode(), response.getBody());
            throw new RuntimeException("Multimodal embedding API returned " + response.getStatusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddings = root.path("output").path("embeddings");
            if (!embeddings.isArray() || embeddings.isEmpty()) {
                throw new RuntimeException("No embeddings in response: " + response.getBody());
            }
            JsonNode embedding = embeddings.get(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new RuntimeException("Empty embedding array in response: " + response.getBody());
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse multimodal embedding response: " + e.getMessage(), e);
        }
    }
}
