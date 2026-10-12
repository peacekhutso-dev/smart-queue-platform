package com.dha.queue.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Calls ml-service's wait-time model when a citizen joins a queue —
 * service-to-service, not through the Gateway (same pattern as
 * document-service's MlServiceClient). Fails open: if ml-service is
 * unreachable or returns something unexpected, QueueService falls back to
 * its queueSize/avgServiceTime heuristic rather than blocking a booking on
 * a downstream ML call.
 *
 * doctor_type/financial_class are hospital-domain categories the model was
 * trained on (Kaggle hospital dataset) that have no DHA equivalent — this
 * system has branches/serviceType, not doctors or insurance classes. Sent
 * as "UNKNOWN" so the pipeline's OneHotEncoder(handle_unknown="ignore")
 * zeroes out those columns instead of asserting a false category. The
 * numeric queue-load features (patients_in_system/arrivals_last_60min,
 * plus time-of-day computed server-side by ml-service) are real DHA signal
 * and are what actually drives the prediction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MlServiceClient {

    @Value("${app.ml-service.url:http://localhost:8001}")
    private String mlServiceUrl;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    private final RestTemplate restTemplate;

    public Optional<Integer> predictWaitTime(int patientsInSystem, int arrivalsLast60Min) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Gateway-Secret", gatewaySharedSecret);

            Map<String, Object> body = Map.of(
                    "doctor_type", "UNKNOWN",
                    "financial_class", "UNKNOWN",
                    "patients_in_system", patientsInSystem,
                    "arrivals_last_60min", arrivalsLast60Min
            );

            Map<?, ?> response = restTemplate.postForObject(
                    mlServiceUrl + "/predict/wait-time",
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (response == null || response.get("estimated_wait_minutes") == null) {
                log.warn("ml-service wait-time response missing estimated_wait_minutes, falling back to heuristic: {}", response);
                return Optional.empty();
            }
            return Optional.of(((Number) response.get("estimated_wait_minutes")).intValue());
        } catch (RestClientException e) {
            log.warn("ml-service unreachable for wait-time prediction, falling back to heuristic: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to parse wait-time prediction response, falling back to heuristic: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
