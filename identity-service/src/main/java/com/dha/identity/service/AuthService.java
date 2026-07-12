package com.dha.identity.service;

import com.dha.identity.dto.request.*;
import com.dha.identity.dto.response.AuthResponse;
import com.dha.identity.dto.response.UserResponse;
import com.dha.identity.entity.User;
import com.dha.identity.repository.UserRepository;
import com.dha.identity.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Mirrors the monolith's ZeroTrustFilter.TWO_FA_PREFIX — the two services
     *  agree on this Redis key shape by convention, not by shared code. */
    private static final String TWO_FA_PREFIX = "2fa:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.zero-trust.two-fa-ttl-hours:8}")
    private int twoFaTtlHours;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .province(request.getProvince())
                .role(User.UserRole.CITIZEN)
                .isVerified(false)
                .isIdVerified(false)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        otpService.generateAndStoreOtp(user.getPhoneNumber());

        String token = issueToken(user);
        return buildAuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailOrPhoneNumber(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getIsActive()) || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = issueToken(user);
        return buildAuthResponse(user, token);
    }

    public void sendOtp(OtpRequest request) {
        otpService.generateAndStoreOtp(request.getIdentifier());
    }

    @Transactional
    public boolean verifyOtp(OtpVerifyRequest request, String currentUserEmail) {
        boolean valid = otpService.verifyOtp(request.getIdentifier(), request.getOtp());
        if (valid) {
            userRepository.findByEmailOrPhoneNumber(currentUserEmail, currentUserEmail)
                    .ifPresent(user -> {
                        user.setIsVerified(true);
                        userRepository.save(user);
                        // Grant 2FA session — monolith's ZeroTrustFilter Gate 2
                        String key = TWO_FA_PREFIX + user.getId();
                        redisTemplate.opsForValue().set(key, "verified", twoFaTtlHours, TimeUnit.HOURS);
                        log.info("2FA session granted for userId={} TTL={}h", user.getId(), twoFaTtlHours);
                    });
        }
        return valid;
    }

    @Transactional
    public Map<String, Object> verifyId(IdVerifyRequest request, String currentUserEmail) {
        String id = request.getSaIdNumber();
        boolean valid = validateSaId(id);

        if (valid) {
            userRepository.findByEmail(currentUserEmail).ifPresent(user -> {
                user.setSaIdNumber(id);
                user.setIsIdVerified(true);
                userRepository.save(user);
            });
        }

        return Map.of(
                "valid", valid,
                "idNumber", id,
                "message", valid ? "ID number verified successfully" : "Invalid SA ID number"
        );
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        // Only generate a code if the account exists, but respond identically either way
        // to the controller so we never leak account existence to the caller.
        userRepository.findByEmailOrPhoneNumber(request.getIdentifier(), request.getIdentifier())
                .ifPresent(user -> passwordResetService.generateAndStoreResetCode(request.getIdentifier()));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!passwordResetService.verifyAndConsumeResetCode(request.getIdentifier(), request.getCode())) {
            throw new IllegalArgumentException("Invalid or expired reset code");
        }
        User user = userRepository.findByEmailOrPhoneNumber(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserResponse(user);
    }

    private String issueToken(User user) {
        return jwtUtil.generateToken(user.getEmail(), Map.of(
                "userId", user.getId().toString(),
                "role", user.getRole().name()
        ));
    }

    // SA ID Luhn Algorithm Validation
    private boolean validateSaId(String id) {
        if (id == null || !id.matches("\\d{13}")) return false;

        try {
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                int digit = Character.getNumericValue(id.charAt(i));
                if (i % 2 == 0) {
                    sum += digit;
                } else {
                    int doubled = digit * 2;
                    sum += doubled > 9 ? doubled - 9 : doubled;
                }
            }
            int checkDigit = (10 - (sum % 10)) % 10;
            return checkDigit == Character.getNumericValue(id.charAt(12));
        } catch (Exception e) {
            return false;
        }
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .isVerified(user.getIsVerified())
                .isIdVerified(user.getIsIdVerified())
                .phoneNumber(user.getPhoneNumber())
                .province(user.getProvince())
                .assignedBranchId(user.getAssignedBranchId())
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .saIdNumber(user.getSaIdNumber())
                .role(user.getRole().name())
                .isVerified(user.getIsVerified())
                .isIdVerified(user.getIsIdVerified())
                .province(user.getProvince())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
