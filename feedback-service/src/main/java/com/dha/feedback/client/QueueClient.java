package com.dha.feedback.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Verifies a queueEntryId submitted with feedback actually belongs to the
 * citizen submitting it, rather than trusting the client's own claim.
 * Forwards the citizen's own already-verified identity (not a separate
 * service identity) plus the shared Gateway secret, so queue-service's
 * ownership check runs as that citizen — an entry belonging to someone else
 * comes back 403 (see queue-service's HeaderAuthFilter/getEntryForRequester).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueueClient {

    @Value("${app.queue-service.url:http://localhost:8088}")
    private String queueServiceUrl;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public Long getEntryBranchId(Long entryId, UUID requesterId, String requesterRole) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", requesterId.toString());
        headers.set("X-User-Role", requesterRole);
        headers.set("X-Gateway-Secret", gatewaySharedSecret);

        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                    queueServiceUrl + "/api/queue/entries/" + entryId,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
            if (data == null) throw new IllegalArgumentException("Queue entry not found");
            return ((Number) data.get("branchId")).longValue();
        } catch (RestClientException e) {
            log.warn("Rejected feedback for queueEntryId={} requesterId={}: {}", entryId, requesterId, e.getMessage());
            throw new SecurityException("Invalid or inaccessible queue entry");
        }
    }
}
