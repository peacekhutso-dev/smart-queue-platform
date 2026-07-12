package com.dha.document.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Calls ml-service's classical-CV quality assessment (blur/brightness/glare)
 * and Claude-vision document classification for uploaded documents —
 * service-to-service, not through the Gateway. Fails open: if ml-service is
 * unreachable or returns something unexpected, the relevant gate is skipped
 * rather than blocking a legitimate upload (same resilience posture as
 * document optimization).
 */
@Component
@Slf4j
public class MlServiceClient {

    @Value("${app.ml-service.url:http://localhost:8001}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record QualityResult(int score, boolean passed, List<String> issues) {
    }

    public record ClassificationResult(String detectedType, boolean verified, String message,
                                        Map<String, String> extractedFields) {
    }

    @SuppressWarnings("unchecked")
    public QualityResult assessQuality(byte[] fileBytes, String filename) {
        try {
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            Map<String, Object> response = restTemplate.postForObject(
                    mlServiceUrl + "/quality/assess",
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (response == null) {
                return new QualityResult(100, true, List.of());
            }

            int score = ((Number) response.get("qualityScore")).intValue();
            boolean passed = (boolean) response.get("passed");
            List<String> issues = (List<String>) response.getOrDefault("issues", List.of());
            return new QualityResult(score, passed, issues);
        } catch (RestClientException e) {
            log.warn("ml-service unreachable for quality assessment, skipping gate: {}", e.getMessage());
            return new QualityResult(100, true, List.of());
        } catch (Exception e) {
            log.warn("Failed to parse quality assessment response, skipping gate: {}", e.getMessage());
            return new QualityResult(100, true, List.of());
        }
    }

    @SuppressWarnings("unchecked")
    public ClassificationResult classifyDocument(byte[] fileBytes, String filename, String expectedType) {
        try {
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("expected_type", expectedType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            Map<String, Object> response = restTemplate.postForObject(
                    mlServiceUrl + "/classify/document",
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (response == null) {
                return new ClassificationResult("UNKNOWN_DOCUMENT", true, "Classification skipped.", Map.of());
            }

            String detectedType = (String) response.get("detectedType");
            boolean verified = (boolean) response.get("verified");
            String message = (String) response.get("message");
            Map<String, String> extractedFields = (Map<String, String>) response.getOrDefault("extractedFields", Map.of());
            return new ClassificationResult(detectedType, verified, message, extractedFields);
        } catch (RestClientException e) {
            log.warn("ml-service unreachable for classification, skipping gate: {}", e.getMessage());
            return new ClassificationResult("UNKNOWN_DOCUMENT", true, "Classification skipped.", Map.of());
        } catch (Exception e) {
            log.warn("Failed to parse classification response, skipping gate: {}", e.getMessage());
            return new ClassificationResult("UNKNOWN_DOCUMENT", true, "Classification skipped.", Map.of());
        }
    }
}
