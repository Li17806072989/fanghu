package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fanghu.platform.mapper.SmsSendRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class SmsCodeServiceTest {
    @Test
    void rejectsInvalidPhoneBeforeCallingProvider() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SmsCodeService service = new SmsCodeService(redis, mock(SmsSendRecordMapper.class));
        ReflectionTestUtils.setField(service, "enabled", true);
        assertThrows(IllegalArgumentException.class, () -> service.send("123", "register", "127.0.0.1"));
        verifyNoInteractions(redis);
    }

    @Test
    void rejectsMockProviderAtStartup() {
        SmsCodeService service = new SmsCodeService(mock(StringRedisTemplate.class), mock(SmsSendRecordMapper.class));
        ReflectionTestUtils.setField(service, "provider", "mock");
        ReflectionTestUtils.setField(service, "exposeCode", false);

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }

    @Test
    void rejectsPlaintextCodeExposureAtStartup() {
        SmsCodeService service = new SmsCodeService(mock(StringRedisTemplate.class), mock(SmsSendRecordMapper.class));
        ReflectionTestUtils.setField(service, "provider", "aliyun");
        ReflectionTestUtils.setField(service, "exposeCode", true);

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }

    @Test
    void rejectsEnabledSmsWithoutProviderAtStartup() {
        SmsCodeService service = new SmsCodeService(mock(StringRedisTemplate.class), mock(SmsSendRecordMapper.class));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "provider", " ");
        ReflectionTestUtils.setField(service, "exposeCode", false);

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }

    @Test
    void acceptsDisabledSmsWithoutProvider() {
        SmsCodeService service = new SmsCodeService(mock(StringRedisTemplate.class), mock(SmsSendRecordMapper.class));
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "provider", "");
        ReflectionTestUtils.setField(service, "exposeCode", false);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(service::validateConfiguration);
    }

    @Test
    void rejectsUnsupportedPurposeBeforeCallingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SmsCodeService service = new SmsCodeService(redis, mock(SmsSendRecordMapper.class));
        ReflectionTestUtils.setField(service, "enabled", true);

        assertThrows(IllegalArgumentException.class, () -> service.send("13800138000", "arbitrary", "127.0.0.1"));

        verifyNoInteractions(redis);
    }

    @Test
    void verifiesAndConsumesStoredCode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fanghu:sms:code:register:13800138000")).thenReturn("123456");
        SmsCodeService service = new SmsCodeService(redis, mock(SmsSendRecordMapper.class));
        org.junit.jupiter.api.Assertions.assertTrue(service.verify("13800138000", "register", "123456"));
        verify(redis).delete("fanghu:sms:code:register:13800138000");
    }
}
