package com.dha.identity.controller;

import com.dha.identity.dto.request.*;
import com.dha.identity.dto.response.ApiResponse;
import com.dha.identity.dto.response.AuthResponse;
import com.dha.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_COOKIE_NAME = "dha_token";

    private final AuthService authService;

    @Value("${app.jwt.expiry}")
    private long jwtExpiryMillis;

    @Value("${app.jwt.cookie-secure:true}")
    private boolean cookieSecure;

    // No auth required — called mid-registration, before any account/JWT
    // exists. See ZeroTrustGlobalFilter's NO_AUTH_PREFIXES.
    @GetMapping("/check-availability")
    public ResponseEntity<ApiResponse<Object>> checkAvailability(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber) {
        return ResponseEntity.ok(ApiResponse.ok(authService.checkAvailability(email, phoneNumber)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return withTokenCookie(response, "Registration successful");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withTokenCookie(response, "Login successful");
    }

    /**
     * Clears the auth cookie. Gate1-only (like verify-otp/verify-id/me) —
     * you need a currently-valid session to log out of it.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        ResponseCookie cleared = ResponseCookie.from(TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .body(ApiResponse.ok("Logged out", null));
    }

    // The JWT is set as an httpOnly cookie, never returned in the response
    // body — a token readable by JS is a token stealable by any XSS on the
    // page. AuthResponse.token is cleared before serializing.
    private ResponseEntity<ApiResponse<AuthResponse>> withTokenCookie(AuthResponse response, String message) {
        String token = response.getToken();
        response.setToken(null);

        ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtExpiryMillis))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(message, response));
    }

    // data is the OTP itself when app.otp.dev-expose-otp is on (local dev
    // only — see AuthService.sendOtp), null otherwise.
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody OtpRequest request) {
        String devOtp = authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("OTP sent successfully", devOtp));
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

    @PostMapping("/confirm-identity")
    public ResponseEntity<ApiResponse<Object>> confirmIdentity(
            @Valid @RequestBody ConfirmIdentityRequest request,
            @RequestHeader("X-User-Email") String userEmail) {
        var result = authService.confirmIdentity(request, userEmail);
        return ResponseEntity.ok(ApiResponse.ok("Identity confirmation processed", result));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> getCurrentUser(@RequestHeader("X-User-Email") String userEmail) {
        var user = authService.getCurrentUser(userEmail);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }
}
