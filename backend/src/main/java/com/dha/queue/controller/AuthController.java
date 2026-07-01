package com.dha.queue.controller;

import com.dha.queue.dto.request.*;
import com.dha.queue.dto.response.ApiResponse;
import com.dha.queue.dto.response.AuthResponse;
import com.dha.queue.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean verified = authService.verifyOtp(request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(verified ? "OTP verified" : "Invalid OTP", verified));
    }

    @PostMapping("/verify-id")
    public ResponseEntity<ApiResponse<Object>> verifyId(
            @Valid @RequestBody IdVerifyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var result = authService.verifyId(request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("ID verification processed", result));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        var user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(user));
    }
}