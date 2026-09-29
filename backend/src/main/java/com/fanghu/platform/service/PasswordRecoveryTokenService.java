package com.fanghu.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryTokenService {
    static final String PURPOSE = "password-recovery";
    static final String KEY_PREFIX = "fanghu:auth:password-recovery:";
    static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public PasswordRecoveryTokenService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String issue(Long userId) {
        if (userId == null) throw new IllegalArgumentException("用户不能为空");
        String token = UUID.randomUUID().toString();
        try {
            String claim = objectMapper.writeValueAsString(new RecoveryClaim(PURPOSE, userId));
            redis.opsForValue().set(KEY_PREFIX + token, claim, TTL);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建找回密码凭证", e);
        }
    }

    public Long consume(String token) {
        if (token == null || token.isBlank()) return null;
        String claim = redis.opsForValue().getAndDelete(KEY_PREFIX + token.trim());
        if (claim == null) return null;
        try {
            RecoveryClaim parsed = objectMapper.readValue(claim, RecoveryClaim.class);
            return PURPOSE.equals(parsed.purpose()) ? parsed.userId() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    record RecoveryClaim(String purpose, Long userId) { }
}
