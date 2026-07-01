package com.dha.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceVerificationService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.face.service-url:http://localhost:8002}")
    private String faceServiceUrl;

    @Value("${app.face.session-ttl-hours:4}")
    private int sessionTtlHours;

    @Value("${app.face.dev-bypass:false}")
    private boolean devBypass;

    private static final String FACE_SESSION_PREFIX = "face:";

    public record EnrollResult(boolean success, String message) {}
    public record VerifyResult(boolean verified, boolean noEnrollment, String message) {}

    // ── Enrollment (first-time setup) ────────────────────────────────────────

    public EnrollResult enrollFace(Long userId, String imageBase64) {
        if (devBypass) {
            log.warn("DEV_BYPASS: face enrollment skipped for userId={}", userId);
            return new EnrollResult(true, "Dev bypass — enrollment accepted");
        }
        try {
            ResponseEntity<Map> res = post("/enroll", Map.of("userId", userId, "imageBase64", imageBase64));
            if (res.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(res.getBody().get("success"))) {
                log.info("Face enrolled userId={}", userId);
                return new EnrollResult(true, "Face enrolled successfully");
            }
            String msg = res.getBody() != null ? String.valueOf(res.getBody().get("message")) : "Rejected";
            return new EnrollResult(false, msg);
        } catch (ResourceAccessException e) {
            log.error("Face service unreachable (enroll): {}", e.getMessage());
            return new EnrollResult(false, "Face service is unavailable — try again later");
        } catch (Exception e) {
            log.error("Enrollment error userId={}: {}", userId, e.getMessage());
            return new EnrollResult(false, "Enrollment failed: " + e.getMessage());
        }
    }

    // ── Verification (session renewal) ───────────────────────────────────────

    public VerifyResult verifyFace(Long userId, String imageBase64) {
        if (devBypass) {
            log.warn("DEV_BYPASS: face verification auto-approved for userId={}", userId);
            grantSession(userId);
            return new VerifyResult(true, false, "Dev bypass — verification accepted");
        }
        try {
            ResponseEntity<Map> res = post("/verify", Map.of("userId", userId, "imageBase64", imageBase64));
            Map<?, ?> body = res.getBody();
            if (body == null) return new VerifyResult(false, false, "Empty response from face service");

            boolean verified     = Boolean.TRUE.equals(body.get("verified"));
            boolean noEnrollment = Boolean.TRUE.equals(body.get("noEnrollment"));
            String  message      = body.containsKey("message") ? String.valueOf(body.get("message")) : "";

            if (verified) grantSession(userId);
            return new VerifyResult(verified, noEnrollment, message);

        } catch (ResourceAccessException e) {
            log.error("Face service unreachable (verify): {}", e.getMessage());
            return new VerifyResult(false, false, "Face service is unavailable — try again later");
        } catch (Exception e) {
            log.error("Verification error userId={}: {}", userId, e.getMessage());
            return new VerifyResult(false, false, "Verification error: " + e.getMessage());
        }
    }

    // ── Redis session helpers ─────────────────────────────────────────────────

    public void grantSession(Long userId) {
        redisTemplate.opsForValue().set(FACE_SESSION_PREFIX + userId, "verified", sessionTtlHours, TimeUnit.HOURS);
        log.info("Face session granted userId={} TTL={}h", userId, sessionTtlHours);
    }

    public void revokeSession(Long userId) {
        redisTemplate.delete(FACE_SESSION_PREFIX + userId);
    }

    public boolean hasActiveSession(Long userId) {
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
