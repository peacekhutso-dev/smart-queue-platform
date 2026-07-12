package com.dha.verification.controller;

import com.dha.verification.dto.request.FaceVerifyRequest;
import com.dha.verification.dto.response.ApiResponse;
import com.dha.verification.service.FaceVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Handles face enrollment (first-time) and verification (session renewal).
 * The Gateway's ZeroTrustGlobalFilter already required a valid JWT to reach
 * here (GATE1_ONLY tier — this endpoint is what *grants* the face gate, so
 * it can't itself require the face gate) and forwards the caller's identity
 * as X-User-Id — no JWT parsing needed in this service at all.
 */
@RestController
@RequestMapping("/api/auth/face")
@RequiredArgsConstructor
public class FaceVerificationController {

    private final FaceVerificationService faceService;

    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enroll(
            @Valid @RequestBody FaceVerifyRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        FaceVerificationService.EnrollResult result = faceService.enrollFace(userId, request.getImageBase64());

        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.ok("Face enrolled successfully",
                    Map.of("enrolled", true, "userId", userId)));
        }
        if (result.livenessFailed()) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message(result.message())
                    .data(Map.of("code", "LIVENESS_FAILED"))
                    .build());
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(result.message()));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @Valid @RequestBody FaceVerifyRequest request,
            @RequestHeader("X-User-Id") UUID userId) {

        FaceVerificationService.VerifyResult result = faceService.verifyFace(userId, request.getImageBase64());

        if (result.livenessFailed()) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message(result.message())
                    .data(Map.of("code", "LIVENESS_FAILED"))
                    .build());
        }

        if (result.noEnrollment()) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("No face enrolled. Please complete face enrollment first.")
                    .data(Map.of("code", "NO_ENROLLMENT"))
                    .build());
        }

        if (result.verified()) {
            return ResponseEntity.ok(ApiResponse.ok("Face verified",
                    Map.of("verified", true, "userId", userId)));
        }

        return ResponseEntity.status(401).body(ApiResponse.error(
                result.message().isBlank() ? "Face does not match. Please try again." : result.message()
        ));
    }
}
