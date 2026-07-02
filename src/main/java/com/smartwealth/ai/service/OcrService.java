package com.smartwealth.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final int MAX_PAGES = 15;
    private static final float RENDER_DPI = 200;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public OcrService() {
        this.restTemplate = new RestTemplate();
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
        this.objectMapper = new ObjectMapper();
    }

    public String ocrPdf(byte[] fileBytes, String fileName) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DASHSCOPE_API_KEY not set, skipping OCR for '{}'", fileName);
            return "";
        }

        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            int pages = document.getNumberOfPages();
            if (pages > MAX_PAGES) {
                log.warn("PDF '{}' has {} pages, exceeds OCR limit of {} — skipping OCR", fileName, pages, MAX_PAGES);
                return "";
            }

            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImage(i, RENDER_DPI / 72f);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpeg", baos);
                String pageText = ocrPage(baos.toByteArray(), i + 1, pages);
                if (pageText != null && !pageText.isBlank()) {
                    result.append(pageText).append("\n");
                }
            }

            log.info("OCR processed '{}': {} pages, {} chars", fileName, pages, result.length());
            return result.toString();
        } catch (Exception e) {
            log.error("OCR failed for '{}'", fileName, e);
            return "";
        }
    }

    /**
     * OCR a single image (photo of a page or question).
     */
    public String ocrImage(byte[] imageBytes) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DASHSCOPE_API_KEY not set, skipping image OCR");
            return "";
        }
        return ocrPage(imageBytes, 1, 1);
    }

    private String ocrPage(byte[] imageBytes, int pageNum, int totalPages) {
        try {
            String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> userMsg = Map.of("role", "user", "content",
                    List.of(Map.of("image", base64Image),
                            Map.of("text", "Extract all text from this document page (" + pageNum + "/" + totalPages
                                    + "). Preserve the original formatting as much as possible. Output only the extracted text, no commentary.")));

            Map<String, Object> requestBody = Map.of(
                    "model", "qwen-vl-ocr",
                    "input", Map.of("messages", List.of(userMsg)),
                    "parameters", Map.of("max_tokens", 4096, "temperature", 0.1));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OCR API error page {}/{}, status: {}", pageNum, totalPages, response.getStatusCode());
                return "";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isArray() && !content.isEmpty()) {
                    return content.get(0).path("text").asText("").trim();
                }
                return content.asText("").trim();
            }
            return "";
        } catch (Exception e) {
            log.warn("OCR page {}/{} failed: {}", pageNum, totalPages, e.getMessage());
            return "";
        }
    }
}
