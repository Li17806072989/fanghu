package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PasswordRecoveryTokenServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordRecoveryTokenService service = new PasswordRecoveryTokenService(redis, objectMapper);

    @Test
    void issuesPurposeBoundTokenWithTenMinuteTtl() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> claim = ArgumentCaptor.forClass(String.class);

        String token = service.issue(17L);

        verify(values).set(key.capture(), claim.capture(), eq(Duration.ofMinutes(10)));
        assertEquals(PasswordRecoveryTokenService.KEY_PREFIX + token, key.getValue());
        PasswordRecoveryTokenService.RecoveryClaim parsed = objectMapper.readValue(
            claim.getValue(), PasswordRecoveryTokenService.RecoveryClaim.class);
        assertEquals(PasswordRecoveryTokenService.PURPOSE, parsed.purpose());
        assertEquals(17L, parsed.userId());
    }

    @Test
    void consumesTokenAtomicallyOnlyOnce() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        String claim = objectMapper.writeValueAsString(
            new PasswordRecoveryTokenService.RecoveryClaim(PasswordRecoveryTokenService.PURPOSE, 17L));
        when(values.getAndDelete(PasswordRecoveryTokenService.KEY_PREFIX + "token"))
            .thenReturn(claim)
            .thenReturn(null);

        assertEquals(17L, service.consume("token"));
        assertNull(service.consume("token"));
        verify(values, org.mockito.Mockito.times(2)).getAndDelete(
            PasswordRecoveryTokenService.KEY_PREFIX + "token");
    }

    @Test
    void rejectsTokenIssuedForAnotherPurpose() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        String claim = objectMapper.writeValueAsString(
            new PasswordRecoveryTokenService.RecoveryClaim("email-verification", 17L));
        when(values.getAndDelete(anyString())).thenReturn(claim);

        assertNull(service.consume("token"));
    }
}
