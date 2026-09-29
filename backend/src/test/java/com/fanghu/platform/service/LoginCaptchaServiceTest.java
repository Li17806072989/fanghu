package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginCaptchaServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @Test
    void issuesSixCharacterCaptchaWithFiveMinuteTtl() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(org.mockito.ArgumentMatchers.anyInt())).thenReturn(0);
        when(redis.opsForValue()).thenReturn(values);
        LoginCaptchaService service = new LoginCaptchaService(redis, random);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        LoginCaptchaService.Captcha captcha = service.issue();

        assertEquals(6, captcha.code().length());
        verify(values).set(key.capture(), eq(captcha.code()), eq(Duration.ofMinutes(5)));
        assertEquals(LoginCaptchaService.KEY_PREFIX + captcha.id(), key.getValue());
    }

    @Test
    void consumesCaptchaOnlyOnce() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(LoginCaptchaService.KEY_PREFIX + "id"))
            .thenReturn("ABC234")
            .thenReturn(null);
        LoginCaptchaService service = new LoginCaptchaService(redis, mock(SecureRandom.class));

        assertTrue(service.consume("id", "abc234"));
        assertFalse(service.consume("id", "ABC234"));
    }
}
