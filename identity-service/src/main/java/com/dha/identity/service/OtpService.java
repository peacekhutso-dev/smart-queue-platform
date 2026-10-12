package com.dha.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${app.otp.length:6}")
    private int otpLength;

    // Same dev-only flag AuthService.sendOtp uses to return the OTP in the
    // API response — reused here so `docker compose logs identity-service`
    // shows it too, without a second flag to keep in sync.
    @Value("${app.otp.dev-expose-otp:false}")
    private boolean devExposeOtp;

    private static final String OTP_PREFIX = "otp:";
    private static final String ATTEMPTS_PREFIX = "otp:attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom random = new SecureRandom();

    public String generateAndStoreOtp(String identifier) {
        String otp = generateOtp();
        String key = OTP_PREFIX + identifier;
        redisTemplate.opsForValue().set(key, otp, expiryMinutes, TimeUnit.MINUTES);
        redisTemplate.delete(ATTEMPTS_PREFIX + identifier);
        // Never log the OTP value itself in a real deployment — it's a
        // 6-digit code standing in for a password. In production: send via
        // Africa's Talking / Twilio. dev-expose-otp is only true in this
        // repo's docker-compose.yml, for local testing.
        if (devExposeOtp) {
            log.info("OTP for {}: {} (expires in {} minutes)", identifier, otp, expiryMinutes);
        } else {
            log.info("OTP generated for {} (expires in {} minutes)", identifier, expiryMinutes);
        }
        return otp;
    }

    public boolean verifyOtp(String identifier, String otp) {
        String attemptsKey = ATTEMPTS_PREFIX + identifier;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, expiryMinutes, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            log.warn("OTP verification locked out for {} after {} attempts", identifier, attempts - 1);
            return false;
        }

        String key = OTP_PREFIX + identifier;
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.toString().equals(otp)) {
            redisTemplate.delete(key);
            redisTemplate.delete(attemptsKey);
            return true;
        }
        return false;
    }

    public boolean hasValidOtp(String identifier) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(OTP_PREFIX + identifier));
    }

    private String generateOtp() {
        int max = (int) Math.pow(10, otpLength);
        int min = (int) Math.pow(10, otpLength - 1);
        return String.valueOf(min + random.nextInt(max - min));
    }
}
