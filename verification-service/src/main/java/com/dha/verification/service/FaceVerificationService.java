package com.dha.verification.service;

import com.dha.verification.client.LuxandLivenessClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceVerificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LuxandLivenessClient luxandClient;

    @Value("${app.face.service-url:http://localhost:8002}")
    private String faceServiceUrl;

    @Value("${app.face.session-ttl-hours:4}")
    private int sessionTtlHours;

    @Value("${app.face.dev-bypass:false}")
    private boolean devBypass;

    private static final String FACE_SESSION_PREFIX = "face:";

    public record EnrollResult(boolean success, String message, boolean livenessFailed) {}
    public record VerifyResult(boolean verified, boolean noEnrollment, String message, boolean livenessFailed) {}

    // ── Enrollment (first-time setup) ────────────────────────────────────────

    public EnrollResult enrollFace(UUID userId, String imageBase64) {
        if (devBypass) {
            log.warn("DEV_BYPASS: face enrollment skipped for userId={}", userId);
            return new EnrollResult(true, "Dev bypass — enrollment accepted", false);
        }

        // Liveness (Luxand) gates the match — see
        // docs/decisions/face-verification-vendor-selection.md for why the
        // match itself stays on the self-hosted face-service below.
        LuxandLivenessClient.LivenessResult liveness = luxandClient.checkLiveness(decodeImage(imageBase64));
        if (!liveness.live()) {
            log.warn("Liveness check failed for enrollment userId={}: {}", userId, liveness.message());
            return new EnrollResult(false, liveness.message(), true);
        }

        try {
            ResponseEntity<Map> res = post("/enroll", Map.of("userId", userId, "imageBase64", imageBase64));
            if (res.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(res.getBody().get("success"))) {
                log.info("Face enrolled userId={}", userId);
                return new EnrollResult(true, "Face enrolled successfully", false);
            }
            String msg = res.getBody() != null ? String.valueOf(res.getBody().get("message")) : "Rejected";
            return new EnrollResult(false, msg, false);
        } catch (ResourceAccessException e) {
            log.error("Face service unreachable (enroll): {}", e.getMessage());
            return new EnrollResult(false, "Face service is unavailable — try again later", false);
        } catch (Exception e) {
            log.error("Enrollment error userId={}: {}", userId, e.getMessage());
            return new EnrollResult(false, "Enrollment failed: " + e.getMessage(), false);
        }
    }

    // ── Verification (session renewal) ───────────────────────────────────────

    public VerifyResult verifyFace(UUID userId, String imageBase64) {
        if (devBypass) {
            log.warn("DEV_BYPASS: face verification auto-approved for userId={}", userId);
            grantSession(userId);
            return new VerifyResult(true, false, "Dev bypass — verification accepted", false);
        }

        LuxandLivenessClient.LivenessResult liveness = luxandClient.checkLiveness(decodeImage(imageBase64));
        if (!liveness.live()) {
            log.warn("Liveness check failed for verification userId={}: {}", userId, liveness.message());
            return new VerifyResult(false, false, liveness.message(), true);
        }

        try {
            ResponseEntity<Map> res = post("/verify", Map.of("userId", userId, "imageBase64", imageBase64));
            Map<?, ?> body = res.getBody();
            if (body == null) return new VerifyResult(false, false, "Empty response from face service", false);

            boolean verified     = Boolean.TRUE.equals(body.get("verified"));
            boolean noEnrollment = Boolean.TRUE.equals(body.get("noEnrollment"));
            String  message      = body.containsKey("message") ? String.valueOf(body.get("message")) : "";

            if (verified) grantSession(userId);
            return new VerifyResult(verified, noEnrollment, message, false);

        } catch (ResourceAccessException e) {
            log.error("Face service unreachable (verify): {}", e.getMessage());
            return new VerifyResult(false, false, "Face service is unavailable — try again later", false);
        } catch (Exception e) {
            log.error("Verification error userId={}: {}", userId, e.getMessage());
            return new VerifyResult(false, false, "Verification error: " + e.getMessage(), false);
        }
    }

    private byte[] decodeImage(String imageBase64) {
        String data = imageBase64.contains(",") ? imageBase64.substring(imageBase64.indexOf(',') + 1) : imageBase64;
        return Base64.getDecoder().decode(data);
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
        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rt.exchange(faceServiceUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
