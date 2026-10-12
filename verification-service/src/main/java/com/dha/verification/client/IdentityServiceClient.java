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
 * Fetches the photo Home Affairs holds for a citizen (their record in
 * identity-service's simulated population register, looked up by the SA ID
 * on their account) — service-to-service via identity-service's
 * /internal/citizen-photo, which the Gateway doesn't route. Citizens never
 * supply this photo themselves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.identity.service-url:http://localhost:8085}")
    private String identityServiceUrl;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    public record CitizenPhoto(String imageBase64, String contentType) {
    }

    @SuppressWarnings("unchecked")
    public Optional<CitizenPhoto> getCitizenPhoto(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Secret", gatewaySharedSecret);

        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                    identityServiceUrl + "/internal/citizen-photo?userId=" + userId,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<?, ?> data = res.getBody() != null ? (Map<?, ?>) res.getBody().get("data") : null;
            if (data == null) return Optional.empty();
            return Optional.of(new CitizenPhoto((String) data.get("imageBase64"), (String) data.get("contentType")));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("identity-service unreachable fetching citizen photo userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
