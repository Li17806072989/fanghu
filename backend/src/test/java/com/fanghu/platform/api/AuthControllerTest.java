package com.fanghu.platform.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fanghu.platform.domain.SysUser;
import com.fanghu.platform.mapper.SysUserMapper;
import com.fanghu.platform.service.SmsCodeService;
import com.fanghu.platform.service.PasswordRecoveryTokenService;
import com.fanghu.platform.service.LoginAttemptLimiter;
import com.fanghu.platform.service.LoginCaptchaService;
import com.fanghu.platform.security.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthControllerTest {
    @Test
    void captchaContainsReusableIdAndImage() {
        var captchas = mock(LoginCaptchaService.class);
        var request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(captchas.issue()).thenReturn(new LoginCaptchaService.Captcha(UUID.randomUUID().toString(), "ABC234"));
        var result = controller(mock(SysUserMapper.class), mock(SmsCodeService.class), captchas).captcha(request);
        assertTrue(result.get("captchaId").length() > 10);
        assertTrue(result.get("imageBase64").startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    void registrationRejectsWeakUsernameBeforeDatabaseWrite() {
        var users = mock(SysUserMapper.class);
        var controller = controller(users, mock(SmsCodeService.class));
        assertThrows(IllegalArgumentException.class, () -> controller.register(new AuthController.RegisterForm("a", "password123", "customer", null, null)));
        verifyNoInteractions(users);
    }

    @Test
    void registrationRequiresVerifiedPhone() {
        var users = mock(SysUserMapper.class);
        var sms = mock(SmsCodeService.class);
        var controller = controller(users, sms);

        assertThrows(IllegalArgumentException.class, () -> controller.register(
            new AuthController.RegisterForm("valid_user", "password123", "customer", null, null)));

        verify(users, never()).insert(any(SysUser.class));
        verifyNoInteractions(sms);
    }

    @Test
    void registrationRejectsInvalidSmsCodeWithoutCreatingUser() {
        var users = mock(SysUserMapper.class);
        var sms = mock(SmsCodeService.class);
        when(sms.verify("13800138000", "register", "123456")).thenReturn(false);
        var controller = controller(users, sms);

        assertThrows(IllegalArgumentException.class, () -> controller.register(
            new AuthController.RegisterForm("valid_user", "password123", "customer", "13800138000", "123456")));

        verify(users, never()).insert(any(SysUser.class));
    }

    @Test
    void invalidNewPasswordDoesNotConsumeRecoveryToken() {
        var recoveryTokens = mock(PasswordRecoveryTokenService.class);
        var controller = new AuthController(mock(SysUserMapper.class), mock(SmsCodeService.class), recoveryTokens,
            mock(LoginCaptchaService.class), mock(LoginAttemptLimiter.class));

        assertThrows(IllegalArgumentException.class, () -> controller.recoveryConfirm(
            new AuthController.RecoveryConfirm("token", "short")));

        verifyNoInteractions(recoveryTokens);
    }

    @Test
    void loginRateLimitStopsAuthenticationBeforeDatabaseLookup() {
        var users = mock(SysUserMapper.class);
        var attempts = mock(LoginAttemptLimiter.class);
        var request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        doThrow(new RateLimitExceededException("登录尝试过于频繁，请稍后再试"))
            .when(attempts).checkAndRecord("user", "127.0.0.1");
        var controller = new AuthController(users, mock(SmsCodeService.class),
            mock(PasswordRecoveryTokenService.class), mock(LoginCaptchaService.class), attempts);

        assertThrows(RateLimitExceededException.class, () -> controller.login(
            new AuthController.LoginForm("user", "password", "captcha", "ABC234"), request));

        verifyNoInteractions(users);
    }

    private AuthController controller(SysUserMapper users, SmsCodeService sms) {
        return controller(users, sms, mock(LoginCaptchaService.class));
    }

    private AuthController controller(SysUserMapper users, SmsCodeService sms, LoginCaptchaService captchas) {
        return new AuthController(users, sms, mock(PasswordRecoveryTokenService.class), captchas,
            mock(LoginAttemptLimiter.class));
    }
}
