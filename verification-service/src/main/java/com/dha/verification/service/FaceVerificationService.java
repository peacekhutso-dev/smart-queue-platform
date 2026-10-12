package com.dha.verification.service;

import com.dha.verification.client.DocumentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceVerificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final DocumentServiceClient documentServiceClient;

    @Value("${app.face.service-url:http://localhost:8002}")
    private String faceServiceUrl;

    @Value("${app.face.session-ttl-hours:4}")
    private int sessionTtlHours;

    @Value("${app.face.dev-bypass:false}")
    private boolean devBypass;

    @Value("${app.gateway.shared-secret}")
    private String gatewaySharedSecret;

    private static final String FACE_SESSION_PREFIX = "face:";

    public record VerifyResult(boolean verified, String message, boolean livenessFailed, boolean noDocumentOnFile) {}

    // ── Verification against the DHA record ────────────────────────────────────
    // DHA already has the citizen's photo on file — the face photo from their
    // verified SA ID/passport upload in document-service. Every call here
    // fetches that photo fresh and hands both it and the live capture to
    // face-service to compare; nothing is enrolled or persisted as a side
    // effect of a scan. A citizen with no verified ID document on file yet has
    // nothing to verify against, so that's rejected here before ever reaching
    // face-service.

    public VerifyResult verifyFace(UUID userId, String imageBase64) {
        if (devBypass) {
            log.warn("DEV_BYPASS: face verification auto-approved for userId={}", userId);
            grantSession(userId);
            return new VerifyResult(true, "Dev bypass — verification accepted", false, false);
        }

        // Liveness gates the match — passive anti-spoofing check, now
        // self-hosted on face-service itself (see face-service/liveness_service.py
        // and docs/decisions/face-verification-vendor-selection.md, which
        // originally chose Luxand Cloud for this step; that ADR is now
        // superseded by this self-hosted swap — see git history for why).
        if (!checkLiveness(imageBase64)) {
            log.warn("Liveness check failed for verification userId={}", userId);
            return new VerifyResult(false, "Liveness check failed — please use a live camera capture", true, false);
        }

        Optional<DocumentServiceClient.FacePhoto> facePhoto = documentServiceClient.getFacePhoto(userId);
        if (facePhoto.isEmpty()) {
            log.warn("No verified ID document on file for userId={} — nothing to verify against", userId);
            return new VerifyResult(false, "No verified ID document on file yet — please complete ID verification first", false, true);
        }

        try {
            ResponseEntity<Map> res = post("/verify", Map.of(
                    "userId", userId,
                    "imageBase64", imageBase64,
                    "referenceImageBase64", facePhoto.get().imageBase64()));
            Map<?, ?> body = res.getBody();
            if (body == null) return new VerifyResult(false, "Empty response from face service", false, false);

            boolean verified = Boolean.TRUE.equals(body.get("verified"));
            String  message  = body.containsKey("message") ? String.valueOf(body.get("message")) : "";

            if (verified) {
                log.info("Face verify PASSED userId={} distance={} similarity={}",
                        userId, body.get("distance"), body.get("similarity"));
                grantSession(userId);
            } else {
                log.warn("Face verify FAILED userId={} reason=\"{}\" distance={} similarity={}",
                        userId, message, body.get("distance"), body.get("similarity"));
            }
            return new VerifyResult(verified, message, false, false);

        } catch (ResourceAccessException e) {
            log.error("Face service unreachable (verify): {}", e.getMessage());
            return new VerifyResult(false, "Face service is unavailable — try again later", false, false);
        } catch (Exception e) {
            log.error("Verification error userId={}: {}", userId, e.getMessage());
            return new VerifyResult(false, "Verification error: " + e.getMessage(), false, false);
        }
    }

    // Fails closed (treats as not-live) on any error — an unreachable or
    // erroring liveness check must never silently let a verification through.
    private boolean checkLiveness(String imageBase64) {
        try {
            ResponseEntity<Map> res = post("/check-liveness", Map.of("imageBase64", imageBase64));
            Map<?, ?> body = res.getBody();
            return body != null && Boolean.TRUE.equals(body.get("live"));
        } catch (Exception e) {
            log.error("Liveness check error: {}", e.getMessage());
            return false;
        }
    }

    // ── Redis session helpers ─────────────────────────────────────────────────

    public void grantSession(UUID userId) {
        redisTemplate.opsForValue().set(FACE_SESSION_PREFIX + userId, "verified", sessionTtlHours, TimeUnit.HOURS);
        log.info("Face session granted userId={} TTL={}h", userId, sessionTtlHours);
    }

    public void revokeSession(UUID userId) {
        redisTemplate.delete(FACE_SESSION_PREFIX + userId);
    }

    public boolean hasActiveSession(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(FACE_SESSION_PREFIX + userId));
    }

    // ── Internal HTTP helper ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> post(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", gatewaySharedSecret);
        return restTemplate.exchange(faceServiceUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
