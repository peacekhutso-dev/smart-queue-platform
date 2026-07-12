package com.dha.verification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Calls Luxand Cloud's liveness-detection endpoint only — this system does
 * NOT use Luxand for the face-match decision. See
 * docs/decisions/face-verification-vendor-selection.md: the actual 1:1
 * match stays on the self-hosted DeepFace face-service so the biometric
 * decision of record stays under our own control, while anti-spoofing
 * liveness (harder to build well, lower blast radius if wrong) is
 * delegated to a vendor. Kept as its own client so it can be swapped for
 * an iBeta-certified vendor later without touching FaceVerificationService.
 */
@Component
@Slf4j
public class LuxandLivenessClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.luxand.base-url:https://api.luxand.cloud}")
    private String baseUrl;

    @Value("${app.luxand.api-key:}")
    private String apiKey;

    public record LivenessResult(boolean live, String message) {}

    public LivenessResult checkLiveness(byte[] imageBytes) {
        if (apiKey.isBlank()) {
            log.error("LUXAND_API_KEY not configured — cannot run liveness check");
            return new LivenessResult(false, "Liveness service is not configured");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("token", apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("photo", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "capture.jpg";
                }
            });

            ResponseEntity<Map> res = restTemplate.exchange(
                    baseUrl + "/photo/liveness", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = res.getBody();
            String result = resBody != null ? String.valueOf(resBody.get("result")) : null;
            boolean live = "real".equalsIgnoreCase(result);

            log.info("Luxand liveness result={}", result);
            return new LivenessResult(live,
                    live ? "Live person confirmed" : "Liveness check failed — please use a live camera capture");

        } catch (Exception e) {
            log.error("Luxand liveness check error: {}", e.getMessage());
            return new LivenessResult(false, "Liveness service unavailable — try again later");
        }
    }
}
