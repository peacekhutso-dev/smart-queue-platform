package com.dha.identity.controller;

import com.dha.identity.dto.request.*;
import com.dha.identity.dto.response.ApiResponse;
import com.dha.identity.dto.response.AuthResponse;
import com.dha.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody OtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("OTP sent successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("If an account exists, a reset code has been sent", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful", null));
    }

    /**
     * These three are self-service ("already logged in") endpoints. The
     * Gateway's ZeroTrustGlobalFilter already verified the caller's JWT
     * (GATE1_ONLY tier — this is exactly what *grants* the 2FA gate, so it
     * can't itself require the 2FA gate) and forwards X-User-Email — no JWT
     * parsing needed here at all.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            @RequestHeader("X-User-Email") String userEmail) {
        boolean verified = authService.verifyOtp(request, userEmail);
        return ResponseEntity.ok(ApiResponse.ok(verified ? "OTP verified" : "Invalid OTP", verified));
    }

    @PostMapping("/verify-id")
    public ResponseEntity<ApiResponse<Object>> verifyId(
            @Valid @RequestBody IdVerifyRequest request,
            @RequestHeader("X-User-Email") String userEmail) {
        var result = authService.verifyId(request, userEmail);
        return ResponseEntity.ok(ApiResponse.ok("ID verification processed", result));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> getCurrentUser(@RequestHeader("X-User-Email") String userEmail) {
        var user = authService.getCurrentUser(userEmail);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }
}
