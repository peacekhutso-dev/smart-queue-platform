package com.dha.queue.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fetches which document categories this citizen already has a
 * passed-validation upload for — service-to-service, not through the
 * Gateway. document-service requires an authenticated caller
 * (anyRequest().authenticated()), so this sets X-Gateway-Secret plus
 * X-User-Id/X-User-Role itself (there's no inbound JWT here to forward),
 * the same trust model verification-service's DocumentServiceClient uses.
 *
 * Deliberately not caught here: unlike CatalogClient (which fails open
 * because an unreachable catalog just means "requirements unknown, skip
 * the check"), a failure here means we can't confirm this citizen actually
 * has the papers — letting the caller's exception propagate (→ 500, retry)
 * fails closed instead of silently letting an unverified booking through.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentClient {

    @Value("${app.document-service.url:http://localhost:8089}")
    private String documentServiceUrl;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public Set<String> getValidatedCategories(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Secret", gatewaySharedSecret);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "CITIZEN");

        ResponseEntity<Map> res = restTemplate.exchange(
                documentServiceUrl + "/api/documents/validated-categories",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<?, ?> body = res.getBody();
        List<String> data = body != null ? (List<String>) body.get("data") : null;
        return data == null ? Set.of() : Set.copyOf(data);
    }
}
