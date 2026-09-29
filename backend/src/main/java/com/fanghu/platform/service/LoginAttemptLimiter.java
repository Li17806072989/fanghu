package com.fanghu.platform.service;

import com.fanghu.platform.security.RateLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptLimiter {
    private static final String KEY_PREFIX = "fanghu:auth:login-attempt:";
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
        "local count = redis.call('INCR', KEYS[1]); "
            + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return count;",
        Long.class);

    private final StringRedisTemplate redis;
    private final int accountLimit;
    private final int ipLimit;
    private final Duration window;
    private final int captchaIpLimit;
    private final Duration captchaWindow;

    public LoginAttemptLimiter(StringRedisTemplate redis,
                               @Value("${fanghu.auth.login-rate.account-limit:10}") int accountLimit,
                               @Value("${fanghu.auth.login-rate.ip-limit:30}") int ipLimit,
                               @Value("${fanghu.auth.login-rate.window-seconds:300}") long windowSeconds,
                               @Value("${fanghu.auth.captcha-rate.ip-limit:60}") int captchaIpLimit,
                               @Value("${fanghu.auth.captcha-rate.window-seconds:60}") long captchaWindowSeconds) {
        this.redis = redis;
        this.accountLimit = Math.max(1, accountLimit);
        this.ipLimit = Math.max(1, ipLimit);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
        this.captchaIpLimit = Math.max(1, captchaIpLimit);
        this.captchaWindow = Duration.ofSeconds(Math.max(1, captchaWindowSeconds));
    }

    public void checkAndRecord(String username, String ipAddress) {
        String accountKey = accountKey(username);
        String ipKey = ipKey(ipAddress);
        Long accountAttempts = increment(accountKey);
        Long ipAttempts = increment(ipKey);
        if (accountAttempts == null || ipAttempts == null
            || accountAttempts > accountLimit || ipAttempts > ipLimit) {
            throw new RateLimitExceededException("登录尝试过于频繁，请稍后再试");
        }
    }

    public void clearAccount(String username) {
        redis.delete(accountKey(username));
    }

    public void checkCaptchaIssue(String ipAddress) {
        Long requests = increment(KEY_PREFIX + "captcha-ip:" + hash(normalizedIp(ipAddress)), captchaWindow);
        if (requests == null || requests > captchaIpLimit) {
            throw new RateLimitExceededException("验证码获取过于频繁，请稍后再试");
        }
    }

    private Long increment(String key) {
        return increment(key, window);
    }

    private Long increment(String key, Duration ttl) {
        return redis.execute(INCREMENT, List.of(key), String.valueOf(ttl.toSeconds()));
    }

    private String accountKey(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return KEY_PREFIX + "account:" + hash(normalized);
    }

    private String ipKey(String ipAddress) {
        return KEY_PREFIX + "ip:" + hash(normalizedIp(ipAddress));
    }

    private String normalizedIp(String ipAddress) {
        return ipAddress == null ? "" : ipAddress.trim();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算登录限流键", e);
        }
    }
}
