package com.dha.verification.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches the DHA record photo (the citizen's verified SA ID/passport photo)
 * from document-service — service-to-service, not through the Gateway, the
 * same way FaceVerificationService calls face-service directly. Sets
 * X-User-Id/X-User-Role itself (there's no inbound JWT here to forward)
 * alongside X-Gateway-Secret, which is exactly what document-service's
 * HeaderAuthFilter requires to trust an asserted identity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.document.service-url:http://localhost:8089}")
    private String documentServiceUrl;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    public record FacePhoto(String imageBase64, String contentType) {
    }

    @SuppressWarnings("unchecked")
    public Optional<FacePhoto> getFacePhoto(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Secret", gatewaySharedSecret);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "CITIZEN");

        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                    documentServiceUrl + "/api/documents/face-photo",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<?, ?> data = res.getBody() != null ? (Map<?, ?>) res.getBody().get("data") : null;
            if (data == null) return Optional.empty();
            return Optional.of(new FacePhoto((String) data.get("imageBase64"), (String) data.get("contentType")));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("document-service unreachable fetching face photo userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
