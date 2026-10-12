package com.dha.identity.service;

import com.dha.identity.dto.request.*;
import com.dha.identity.dto.response.AuthResponse;
import com.dha.identity.dto.response.UserResponse;
import com.dha.identity.entity.CitizenRecord;
import com.dha.identity.entity.User;
import com.dha.identity.repository.CitizenRecordRepository;
import com.dha.identity.repository.UserRepository;
import com.dha.identity.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Mirrors the monolith's ZeroTrustFilter.TWO_FA_PREFIX — the two services
     *  agree on this Redis key shape by convention, not by shared code. */
    private static final String TWO_FA_PREFIX = "2fa:";

    /** Mirrors ZeroTrustGlobalFilter's IDENTITY_PREFIX (api-gateway) — same convention as TWO_FA_PREFIX above. */
    private static final String IDENTITY_CONFIRMED_PREFIX = "identity-confirmed:";

    private final UserRepository userRepository;
    private final CitizenRecordRepository citizenRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.zero-trust.two-fa-ttl-hours:8}")
    private int twoFaTtlHours;

    @Value("${app.zero-trust.identity-confirm-ttl-hours:8}")
    private int identityConfirmTtlHours;

    // Dev-only: no SMS/email provider is wired up (see OtpService), so with
    // no logging of the OTP value either, there's otherwise no way to
    // complete the 2FA flow locally short of reading it out of Redis
    // directly. Never set true outside local dev — this hands the OTP back
    // to whoever can see the HTTP response.
    @Value("${app.otp.dev-expose-otp:false}")
    private boolean devExposeOtp;

    // Backs the registration form's instant email/phone availability check —
    // a plain unique-index lookup, so it's cheap enough to call on every
    // field blur rather than only surfacing "already registered" at final
    // submit, several steps after the citizen typed it.
    public Map<String, Boolean> checkAvailability(String email, String phoneNumber) {
        Map<String, Boolean> result = new HashMap<>();
        if (email != null && !email.isBlank()) {
            result.put("emailTaken", userRepository.existsByEmail(email));
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            result.put("phoneTaken", userRepository.existsByPhoneNumber(phoneNumber));
        }
        return result;
    }

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
        log.info("Registered userId={} email={}", user.getId(), user.getEmail());

        String token = issueToken(user);
        return buildAuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailOrPhoneNumber(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> {
                    log.warn("Login FAILED identifier={} reason=no account with that email/phone", request.getIdentifier());
                    return new IllegalArgumentException("Invalid credentials");
                });

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Login FAILED userId={} reason=account inactive", user.getId());
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login FAILED userId={} reason=incorrect password", user.getId());
            throw new IllegalArgumentException("Invalid credentials");
        }

        log.info("Login PASSED userId={} email={}", user.getId(), user.getEmail());
        // Login has no OTP re-entry step of its own — a valid password is
        // this app's re-authentication proof, so it must (re-)grant the 2FA
        // gate itself, the same way verify-otp does at registration.
        // Without this, the gate's TTL (twoFaTtlHours) simply expires after
        // registration and can never be renewed — every returning citizen
        // past that window gets force-logged-out on their first FULL-tier
        // action with nowhere to go, since login itself doesn't ask for OTP.
        String twoFaKey = TWO_FA_PREFIX + user.getId();
        redisTemplate.opsForValue().set(twoFaKey, "verified", twoFaTtlHours, TimeUnit.HOURS);
        String token = issueToken(user);
        return buildAuthResponse(user, token);
    }

    public String sendOtp(OtpRequest request) {
        String otp = otpService.generateAndStoreOtp(request.getIdentifier());
        return devExposeOtp ? otp : null;
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
                        log.info("OTP verify PASSED userId={} — 2FA session granted TTL={}h", user.getId(), twoFaTtlHours);
                    });
        } else {
            log.warn("OTP verify FAILED identifier={} reason=incorrect or expired OTP", request.getIdentifier());
        }
        return valid;
    }

    @Transactional
    public Map<String, Object> verifyId(IdVerifyRequest request, String currentUserEmail) {
        String id = request.getSaIdNumber();
        boolean valid = validateSaId(id);

        if (valid) {
            User currentUser = userRepository.findByEmail(currentUserEmail).orElse(null);

            // sa_id_number has a DB-level unique constraint (one real person,
            // one account) — check proactively instead of letting a second
            // registrant hit an unhandled DataIntegrityViolationException.
            // Excludes the current user so re-submitting your own already-set
            // ID (e.g. retrying this step) doesn't false-positive as a clash.
            boolean claimedByAnotherAccount = userRepository.findBySaIdNumber(id)
                    .filter(owner -> currentUser == null || !owner.getId().equals(currentUser.getId()))
                    .isPresent();

            if (claimedByAnotherAccount) {
                log.warn("ID verify FAILED email={} idNumber={} reason=already claimed by another account",
                        currentUserEmail, id);
                return Map.of(
                        "valid", false,
                        "idNumber", id,
                        "message", "This ID number is already registered to another account"
                );
            }

            if (currentUser != null) {
                currentUser.setSaIdNumber(id);
                currentUser.setIsIdVerified(true);
                userRepository.save(currentUser);
            }
            log.info("ID verify PASSED userId={} idNumber={}", currentUser != null ? currentUser.getId() : null, id);
        } else {
            log.warn("ID verify FAILED email={} idNumber={} reason=failed Luhn check", currentUserEmail, id);
        }

        return Map.of(
                "valid", valid,
                "idNumber", id,
                "message", valid ? "ID number verified successfully" : "Invalid SA ID number"
        );
    }

    /**
     * Zero Trust "gate 1.5": proves the citizen logging in is who Home
     * Affairs' own record (CitizenRecord, seeded — see seed.CitizenRecordSeeder)
     * says that SA ID number belongs to. Only the SA ID number is asked for
     * here — the name being checked is the account's own firstName/lastName
     * from registration, not a client-submitted value (nothing to re-type,
     * and nothing for a client to spoof). Returns a result map rather than
     * throwing on a normal mismatch — same convention as verifyId/verifyOtp,
     * since a wrong ID here is retryable, not a hard error. Grants the Redis
     * key ZeroTrustGlobalFilter checks for any FULL-tier route.
     */
    @Transactional
    public Map<String, Object> confirmIdentity(ConfirmIdentityRequest request, String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (currentUser.getSaIdNumber() == null) {
            log.warn("Identity confirm FAILED userId={} reason=no saIdNumber on account yet (ID verification incomplete)",
                    currentUser.getId());
            return Map.of("valid", false, "message", "Please complete ID verification first.");
        }
        if (!currentUser.getSaIdNumber().equals(request.getSaIdNumber())) {
            log.warn("Identity confirm FAILED userId={} reason=given ID does not match account's own saIdNumber",
                    currentUser.getId());
            return Map.of("valid", false, "message", "This ID number does not match your account.");
        }

        Optional<CitizenRecord> record = citizenRecordRepository.findById(request.getSaIdNumber());
        if (record.isEmpty()) {
            log.warn("Identity confirm FAILED userId={} idNumber={} reason=no seeded Home Affairs record for this ID",
                    currentUser.getId(), request.getSaIdNumber());
            return Map.of("valid", false, "message", "No Home Affairs record found for this ID number.");
        }

        CitizenRecord citizenRecord = record.get();
        boolean namesMatch = citizenRecord.getFirstName().trim().equalsIgnoreCase(currentUser.getFirstName().trim())
                && citizenRecord.getLastName().trim().equalsIgnoreCase(currentUser.getLastName().trim());
        if (!namesMatch) {
            log.warn("Identity confirm FAILED userId={} idNumber={} reason=name mismatch (account=\"{} {}\" vs record=\"{} {}\")",
                    currentUser.getId(), request.getSaIdNumber(),
                    currentUser.getFirstName(), currentUser.getLastName(),
                    citizenRecord.getFirstName(), citizenRecord.getLastName());
            return Map.of("valid", false, "message", "Name does not match the Home Affairs record for this ID number.");
        }

        String key = IDENTITY_CONFIRMED_PREFIX + currentUser.getId();
        redisTemplate.opsForValue().set(key, "confirmed", identityConfirmTtlHours, TimeUnit.HOURS);
        log.info("Identity confirm PASSED userId={} idNumber={} TTL={}h",
                currentUser.getId(), request.getSaIdNumber(), identityConfirmTtlHours);

        return Map.of("valid", true, "message", "Identity confirmed against Home Affairs record");
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
