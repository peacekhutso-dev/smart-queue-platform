package com.dha.identity.controller;

import com.dha.identity.dto.response.ApiResponse;
import com.dha.identity.service.WebAuthnService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * STAFF/ADMIN fingerprint gate — WebAuthn (Touch ID / Windows Hello / Android fingerprint).
 * Lives under /api/auth/webauthn so it routes alongside the rest of Identity's auth surface.
 * GATE1_ONLY at the Gateway — see AuthController for why these trust X-User-Email
 * instead of parsing a JWT themselves.
 */
@RestController
@RequestMapping("/api/auth/webauthn")
@RequiredArgsConstructor
public class WebAuthnController {

    private final WebAuthnService webAuthnService;
    private final ObjectMapper objectMapper;

    @PostMapping("/register/options")
    public ResponseEntity<ApiResponse<String>> registerOptions(@RequestHeader("X-User-Email") String userEmail) {
        String optionsJson = webAuthnService.startRegistration(userEmail);
        return ResponseEntity.ok(ApiResponse.ok(optionsJson));
    }

    @PostMapping("/register/verify")
    @SneakyThrows
    public ResponseEntity<ApiResponse<Void>> registerVerify(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody JsonNode credential) {
        webAuthnService.finishRegistration(userEmail, objectMapper.writeValueAsString(credential));
        return ResponseEntity.ok(ApiResponse.ok("Fingerprint enrolled", null));
    }

    @PostMapping("/authenticate/options")
    public ResponseEntity<ApiResponse<String>> authenticateOptions(@RequestHeader("X-User-Email") String userEmail) {
        String optionsJson = webAuthnService.startAuthentication(userEmail);
        return ResponseEntity.ok(ApiResponse.ok(optionsJson));
    }

    @PostMapping("/authenticate/verify")
    @SneakyThrows
    public ResponseEntity<ApiResponse<Boolean>> authenticateVerify(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody JsonNode credential) {
        webAuthnService.finishAuthentication(userEmail, objectMapper.writeValueAsString(credential));
        return ResponseEntity.ok(ApiResponse.ok("Fingerprint verified", true));
    }
}
