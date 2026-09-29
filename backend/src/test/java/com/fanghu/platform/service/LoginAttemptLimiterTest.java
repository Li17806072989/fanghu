package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fanghu.platform.security.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class LoginAttemptLimiterTest {
    @Test
    void acceptsAttemptWithinAccountAndIpLimits() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(any(), anyList(), anyString());
        LoginAttemptLimiter limiter = limiter(redis);

        assertDoesNotThrow(() -> limiter.checkAndRecord("User_Name", "127.0.0.1"));

        verify(redis, times(2)).execute(any(), anyList(), anyString());
    }

    @Test
    void rejectsAttemptWhenAccountLimitIsExceeded() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(11L).doReturn(1L).when(redis).execute(any(), anyList(), anyString());
        LoginAttemptLimiter limiter = limiter(redis);

        assertThrows(RateLimitExceededException.class,
            () -> limiter.checkAndRecord("user_name", "127.0.0.1"));
    }

    @Test
    void successfulLoginClearsOnlyAccountCounter() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LoginAttemptLimiter limiter = limiter(redis);

        limiter.clearAccount("user_name");

        verify(redis).delete(org.mockito.ArgumentMatchers.matches("fanghu:auth:login-attempt:account:[0-9a-f]{64}"));
    }

    @Test
    void rejectsExcessiveCaptchaRequestsByIp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(61L).when(redis).execute(any(), anyList(), anyString());

        assertThrows(RateLimitExceededException.class, () -> limiter(redis).checkCaptchaIssue("127.0.0.1"));
    }

    private LoginAttemptLimiter limiter(StringRedisTemplate redis) {
        return new LoginAttemptLimiter(redis, 10, 30, 300, 60, 60);
    }
}
