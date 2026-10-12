package com.dha.queue.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Fetches a service's required documents from catalog-service so joinQueue
 * can check them before booking (see QueueService.requireDocumentsOnFile).
 * GET /api/catalog/** is public (catalog-service's SecurityConfig permits it
 * unauthenticated), same as BranchClient's call to branch-service.
 *
 * Fails open — if catalog-service is unreachable we don't know what's
 * required, so booking proceeds unchecked rather than blocking every
 * service-wide because one downstream catalog lookup failed (same
 * resilience posture as document-service's MlServiceClient).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogClient {

    @Value("${app.catalog-service.url:http://localhost:8091}")
    private String catalogServiceUrl;

    private final RestTemplate restTemplate;

    public record DocumentRequirement(String documentCategory, String label, boolean required) {
    }

    @SuppressWarnings("unchecked")
    public List<DocumentRequirement> getRequiredDocuments(String serviceCode) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    catalogServiceUrl + "/api/catalog/services/" + serviceCode + "/documents", Map.class);
            List<Map<String, Object>> data = response != null ? (List<Map<String, Object>>) response.get("data") : null;
            if (data == null) return List.of();

            return data.stream()
                    .map(d -> new DocumentRequirement(
                            (String) d.get("documentCategory"),
                            (String) d.get("label"),
                            Boolean.TRUE.equals(d.get("required"))))
                    .toList();
        } catch (RestClientException e) {
            log.warn("catalog-service unreachable fetching document requirements for serviceCode={}: {}", serviceCode, e.getMessage());
            return List.of();
        }
    }
}
