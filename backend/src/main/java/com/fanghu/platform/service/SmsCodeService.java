package com.fanghu.platform.service;

import com.fanghu.platform.domain.SmsSendRecord;
import com.fanghu.platform.mapper.SmsSendRecordMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsCodeService {
    private static final Set<String> PUBLIC_PURPOSES = Set.of("register", "recovery");
    private final StringRedisTemplate redis;
    private final SmsSendRecordMapper records;
    @Value("${fanghu.sms.enabled:false}") private boolean enabled;
    @Value("${fanghu.sms.provider:}") private String provider;
    @Value("${fanghu.sms.code-expire-seconds:300}") private long codeExpire;
    @Value("${fanghu.sms.send-interval-seconds:60}") private long interval;
    @Value("${fanghu.sms.daily-limit:20}") private long dailyLimit;
    @Value("${fanghu.sms.expose-code:false}") private boolean exposeCode;

    @PostConstruct
    public void validateConfiguration() {
        String configuredProvider = provider == null ? "" : provider.trim();
        if (exposeCode) {
            throw new IllegalStateException("不允许启用短信验证码明文暴露（fanghu.sms.expose-code 必须为 false）");
        }
        if ("mock".equalsIgnoreCase(configuredProvider)) {
            throw new IllegalStateException("不允许使用 mock 短信服务，请配置真实短信 provider");
        }
        if (enabled && configuredProvider.isBlank()) {
            throw new IllegalStateException("短信服务已启用但未配置 provider");
        }
    }

    public Map<String, Object> send(String phone, String purpose, String ip) {
        if (!enabled) throw new IllegalStateException("短信服务未启用");
        if (phone == null || !phone.matches("1[3-9]\\d{9}")) throw new IllegalArgumentException("请输入有效的手机号");
        String p = purpose == null ? "" : purpose.trim().toLowerCase();
        if (!PUBLIC_PURPOSES.contains(p)) throw new IllegalArgumentException("不支持的短信验证码用途");
        String intervalKey = "fanghu:sms:interval:" + p + ":" + phone;
        if (Boolean.TRUE.equals(redis.hasKey(intervalKey))) throw new IllegalArgumentException("发送过于频繁，请稍后再试");
        String dayKey = "fanghu:sms:daily:" + LocalDate.now() + ":" + phone;
        Long count = redis.opsForValue().increment(dayKey);
        if (count != null && count == 1) redis.expire(dayKey, Duration.ofDays(1));
        if (count != null && count > dailyLimit) throw new IllegalArgumentException("今日短信发送次数已达上限");
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        String requestId = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set("fanghu:sms:code:" + p + ":" + phone, code, Duration.ofSeconds(codeExpire));
        redis.opsForValue().set(intervalKey, "1", Duration.ofSeconds(interval));
        SmsSendRecord record = new SmsSendRecord(); record.setPhone(phone); record.setPurpose(p); record.setProvider(provider); record.setRequestId(requestId); record.setCodeHash(hash(code)); record.setStatus("SENT"); record.setIpAddress(ip); record.setExpiresAt(LocalDateTime.now().plusSeconds(codeExpire)); records.insert(record);
        return Map.of("requestId", requestId, "expiresIn", codeExpire, "retryAfter", interval,
            "provider", provider);
    }

    public boolean verify(String phone, String purpose, String code) {
        if (phone == null || code == null) return false;
        String key = "fanghu:sms:code:" + (purpose == null ? "login" : purpose) + ":" + phone;
        String expected = redis.opsForValue().get(key);
        if (expected == null || !expected.equals(code.trim())) return false;
        redis.delete(key); return true;
    }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
