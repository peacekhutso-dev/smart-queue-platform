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
 * Verifies a live selfie against a citizen's DHA record photo — the face
 * photo from their verified SA ID/passport document, fetched fresh from
 * document-service on every call (see FaceVerificationService). There is no
 * enrollment endpoint: a citizen with no verified ID document on file has
 * nothing to verify against, so that request is rejected rather than ever
 * treated as a pass. The Gateway's ZeroTrustGlobalFilter already required a
 * valid JWT to reach here (GATE1_ONLY tier — this endpoint is what *grants*
 * the face gate, so it can't itself require the face gate) and forwards the
 * caller's identity as X-User-Id — no JWT parsing needed in this service
 * at all.
 */
@RestController
@RequestMapping("/api/auth/face")
@RequiredArgsConstructor
public class FaceVerificationController {

    private final FaceVerificationService faceService;

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

        if (result.noDocumentOnFile()) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message(result.message())
                    .data(Map.of("code", "NO_ID_DOCUMENT"))
                    .build());
        }

        if (result.verified()) {
            return ResponseEntity.ok(ApiResponse.ok("Identity verified against DHA record",
                    Map.of("verified", true, "userId", userId)));
        }

        // 400, not 401 — a mismatched face is a retry-able outcome, not an
        // invalid/expired JWT. The frontend's Axios interceptor treats any
        // 401 as "session invalid" and force-logs-out + hard-redirects to
        // /login globally — a 401 here previously meant a normal failed
        // face match (this endpoint's JWT was fine, gate1 already passed
        // to even reach it) kicked the user all the way back to login
        // instead of reaching FaceVerifyPage/FaceScanModal's own retry UI.
        return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                .success(false)
                .message(result.message().isBlank() ? "Face does not match. Please try again." : result.message())
                .data(Map.of("code", "FACE_MISMATCH"))
                .build());
    }
}
