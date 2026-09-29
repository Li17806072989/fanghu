package com.fanghu.platform.api;

import com.fanghu.platform.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sms")
public class SmsController {
    private final SmsCodeService sms;
    public SmsController(SmsCodeService sms) { this.sms = sms; }

    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody SendRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        return sms.send(request.phone(), request.purpose(), ip);
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody VerifyRequest request) {
        return Map.of("valid", sms.verify(request.phone(), request.purpose(), request.code()));
    }
    public record SendRequest(String phone, String purpose) { }
    public record VerifyRequest(String phone, String purpose, String code) { }
}
