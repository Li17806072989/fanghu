package com.fanghu.platform.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginCaptchaService {
    static final String KEY_PREFIX = "fanghu:auth:captcha:";
    static final Duration TTL = Duration.ofMinutes(5);
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final StringRedisTemplate redis;
    private final SecureRandom random;

    @Autowired
    public LoginCaptchaService(StringRedisTemplate redis) {
        this(redis, new SecureRandom());
    }

    LoginCaptchaService(StringRedisTemplate redis, SecureRandom random) {
        this.redis = redis;
        this.random = random;
    }

    public Captcha issue() {
        String id = UUID.randomUUID().toString();
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        redis.opsForValue().set(KEY_PREFIX + id, code.toString(), TTL);
        return new Captcha(id, code.toString());
    }

    public boolean consume(String id, String answer) {
        if (id == null || id.isBlank() || answer == null || answer.isBlank()) return false;
        String expected = redis.opsForValue().getAndDelete(KEY_PREFIX + id.trim());
        return expected != null && expected.equalsIgnoreCase(answer.trim());
    }

    public record Captcha(String id, String code) { }
}
