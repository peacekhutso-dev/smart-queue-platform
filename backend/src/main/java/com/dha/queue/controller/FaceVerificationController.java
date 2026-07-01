package com.dha.queue.controller;

import com.dha.queue.dto.request.FaceVerifyRequest;
import com.dha.queue.dto.response.ApiResponse;
import com.dha.queue.repository.UserRepository;
import com.dha.queue.service.FaceVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles face enrollment (first-time) and verification (session renewal).
 * Both endpoints are exempt from ZeroTrustFilter (/api/auth/** prefix).
 */
@RestController
@RequestMapping("/api/auth/face")
@RequiredArgsConstructor
public class FaceVerificationController {

    private final FaceVerificationService faceService;
    private final UserRepository userRepository;

    /** First-time face enrollment — stores reference embedding in face service. */
    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enroll(
            @Valid @RequestBody FaceVerifyRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long userId = resolveId(principal);
        FaceVerificationService.EnrollResult result = faceService.enrollFace(userId, request.getImageBase64());

        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.ok("Face enrolled successfully",
                    Map.of("enrolled", true, "userId", userId)));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(result.message()));
    }

    /**
     * Face verification — compares live capture against stored embedding.
     * On success, writes face:{userId} to Redis (TTL = app.face.session-ttl-hours).
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @Valid @RequestBody FaceVerifyRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long userId = resolveId(principal);
        FaceVerificationService.VerifyResult result = faceService.verifyFace(userId, request.getImageBase64());

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

    private Long resolveId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
