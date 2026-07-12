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

    private static final String OTP_PREFIX = "otp:";
    private static final SecureRandom random = new SecureRandom();

    public String generateAndStoreOtp(String identifier) {
        String otp = generateOtp();
        String key = OTP_PREFIX + identifier;
        redisTemplate.opsForValue().set(key, otp, expiryMinutes, TimeUnit.MINUTES);
        // In production: send via Africa's Talking / Twilio
        log.info("OTP for {}: {} (expires in {} minutes)", identifier, otp, expiryMinutes);
        return otp;
    }

    public boolean verifyOtp(String identifier, String otp) {
        String key = OTP_PREFIX + identifier;
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.toString().equals(otp)) {
            redisTemplate.delete(key);
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
