package com.dha.queue.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * This service still needs branch config (capacity/counters/service time/
 * name/address) for wait-time math and display. Branch lives in
 * branch-service, so this calls its public GET /api/branches/{id} directly
 * (service-to-service, not through the Gateway) rather than joining a local
 * table. This stays a direct synchronous call rather than an event-fed cache
 * — branch config changes rarely, unlike the live queue counts branch-service
 * needs from this service, which change constantly (see RabbitMQConfig).
 */
@Component
@Slf4j
public class BranchClient {

    @Value("${app.branch-service.url:http://localhost:8087}")
    private String branchServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record BranchInfo(
            Long id, String name, String address,
            Integer maxCapacity, Integer activeCounters, Integer avgServiceTimeMinutes
    ) {
    }

    @SuppressWarnings("unchecked")
    public BranchInfo getBranch(Long branchId) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    branchServiceUrl + "/api/branches/" + branchId, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                throw new IllegalArgumentException("Branch not found");
            }
            return new BranchInfo(
                    ((Number) data.get("id")).longValue(),
                    (String) data.get("name"),
                    (String) data.get("address"),
                    (Integer) data.get("maxCapacity"),
                    (Integer) data.get("activeCounters"),
                    (Integer) data.get("avgServiceTimeMinutes")
            );
        } catch (RestClientException e) {
            log.error("branch-service unreachable for branchId={}: {}", branchId, e.getMessage());
            throw new IllegalArgumentException("Branch not found");
        }
    }
}
