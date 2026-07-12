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
public class PasswordResetService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.password-reset.expiry-minutes:15}")
    private int expiryMinutes;

    private static final String RESET_PREFIX = "pwreset:";
    private static final SecureRandom random = new SecureRandom();

    public String generateAndStoreResetCode(String identifier) {
        String code = generateCode();
        redisTemplate.opsForValue().set(RESET_PREFIX + identifier, code, expiryMinutes, TimeUnit.MINUTES);
        // In production: send via Africa's Talking / Twilio / email provider
        log.info("Password reset code for {}: {} (expires in {} minutes)", identifier, code, expiryMinutes);
        return code;
    }

    public boolean verifyAndConsumeResetCode(String identifier, String code) {
        String key = RESET_PREFIX + identifier;
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.toString().equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    private String generateCode() {
        int code = 100_000 + random.nextInt(900_000);
        return String.valueOf(code);
    }
}
