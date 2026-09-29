package com.fanghu.platform.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.SysUser;
import com.fanghu.platform.mapper.SysUserMapper;
import com.fanghu.platform.service.SmsCodeService;
import com.fanghu.platform.service.PasswordRecoveryTokenService;
import com.fanghu.platform.service.LoginAttemptLimiter;
import com.fanghu.platform.service.LoginCaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SysUserMapper users;
    private final SmsCodeService sms;
    private final PasswordRecoveryTokenService recoveryTokens;
    private final LoginCaptchaService captchas;
    private final LoginAttemptLimiter loginAttempts;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper users, SmsCodeService sms, PasswordRecoveryTokenService recoveryTokens,
                          LoginCaptchaService captchas, LoginAttemptLimiter loginAttempts) {
        this.users = users;
        this.sms = sms;
        this.recoveryTokens = recoveryTokens;
        this.captchas = captchas;
        this.loginAttempts = loginAttempts;
    }

    @GetMapping("/captcha")
    public Map<String, String> captcha(HttpServletRequest request) {
        loginAttempts.checkCaptchaIssue(request.getRemoteAddr());
        LoginCaptchaService.Captcha captcha = captchas.issue();
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='150' height='40'>"
            + "<rect width='100%' height='100%' fill='#eef3f5'/>"
            + "<path d='M0 13L150 29M0 31L150 9' stroke='#91a8b2'/>"
            + "<text x='13' y='28' font-size='22' letter-spacing='3' font-family='Arial' fill='#173b4d'>"
            + captcha.code() + "</text></svg>";
        return Map.of("captchaId", captcha.id(), "imageBase64", "data:image/svg+xml;base64,"
            + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)));
    }

    @PostMapping("/register")
    @Transactional
    public Map<String, String> register(@RequestBody RegisterForm form) {
        String username = form.username() == null ? "" : form.username().trim();
        String accountType = form.accountType() == null ? "" : form.accountType().trim().toLowerCase();
        if (!username.matches("[a-zA-Z0-9_]{4,32}")) {
            throw new IllegalArgumentException("用户名需为4至32位字母、数字或下划线");
        }
        if (form.password() == null || form.password().length() < 8 || form.password().length() > 64) {
            throw new IllegalArgumentException("密码长度需为8至64位");
        }
        if (users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, username)) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (!accountType.equals("supplier") && !accountType.equals("customer")) {
            throw new IllegalArgumentException("请选择客户账号或供应商账号");
        }
        String phone = form.phone() == null ? "" : form.phone().trim();
        if (!phone.matches("1[3-9]\\d{9}")) throw new IllegalArgumentException("请输入有效的手机号");
        if (users.byPhone(phone) != null) throw new IllegalArgumentException("手机号已绑定其他账号");
        if (!sms.verify(phone, "register", form.smsCode())) throw new IllegalArgumentException("短信验证码错误或已过期");
        Long roleId = users.roleId(accountType);
        if (roleId == null) {
            throw new IllegalStateException("账号角色尚未初始化，请联系管理员");
        }
        SysUser user = new SysUser();
        user.setUserName(username);
        user.setNickName(username);
        user.setUserType("sys_user");
        user.setPassword(passwords.encode(form.password()));
        user.setStatus("0");
        user.setDelFlag("0");
        user.setPhone(phone); user.setPhoneVerified(1);
        users.insert(user);
        users.assignRole(user.getUserId(), roleId);
        String typeName = accountType.equals("supplier") ? "供应商" : "客户";
        return Map.of("message", typeName + "账号创建成功，请登录");
    }

    @PostMapping("/password/recovery/request")
    public Map<String, String> recoveryRequest(@RequestBody RecoveryRequest form) {
        String phone = form.phone() == null ? "" : form.phone().trim();
        SysUser user = form.username() == null ? null : users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, form.username().trim()));
        if (user == null || phone.isBlank() || !phone.equals(user.getPhone()) || !sms.verify(phone, "recovery", form.smsCode())) throw new IllegalArgumentException("账号、手机号或短信验证码不正确");
        String token = recoveryTokens.issue(user.getUserId());
        return Map.of("token", token, "expiresIn", "600");
    }

    @PostMapping("/password/recovery/confirm")
    public Map<String, String> recoveryConfirm(@RequestBody RecoveryConfirm form) {
        if (form.newPassword() == null || form.newPassword().length() < 8 || form.newPassword().length() > 64) throw new IllegalArgumentException("密码长度需为8至64位");
        Long userId = recoveryTokens.consume(form.token());
        if (userId == null) throw new IllegalArgumentException("找回密码凭证已失效");
        SysUser user = users.selectById(userId);
        if (user == null) throw new IllegalArgumentException("找回密码凭证已失效");
        user.setPassword(passwords.encode(form.newPassword())); users.updateById(user);
        return Map.of("message", "密码重置成功");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginForm form, HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String username = form.username() == null ? "" : form.username().trim();
        loginAttempts.checkAndRecord(username, ipAddress);
        boolean validCaptcha = captchas.consume(form.captchaId(), form.captcha());
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, username));
        if (!validCaptcha || user == null
            || form.password() == null || !"0".equals(user.getStatus())
            || !passwords.matches(form.password(), user.getPassword())) {
            throw new IllegalArgumentException("用户名、密码或验证码错误");
        }
        List<String> roles = users.roles(user.getUserId());
        if (roles.isEmpty()) throw new IllegalArgumentException("账号尚未分配角色，请联系平台管理员");
        loginAttempts.clearAccount(username);
        StpUtil.login(user.getUserId());
        return Map.of("token", StpUtil.getTokenValue(), "user", user.getUserName(),
            "roles", roles, "permissions", users.permissions(user.getUserId()));
    }

    @SaCheckLogin
    @GetMapping("/session")
    public Map<String, Object> session() {
        SysUser user = users.selectById(StpUtil.getLoginIdAsLong());
        if (user == null || !"0".equals(user.getStatus())) {
            StpUtil.logout();
            throw new IllegalArgumentException("账号不存在或已停用");
        }
        return Map.of("token", StpUtil.getTokenValue(), "user", user.getUserName(),
            "roles", users.roles(user.getUserId()), "permissions", users.permissions(user.getUserId()));
    }

    @PostMapping("/logout")
    public void logout() {
        StpUtil.logout();
    }

    public record LoginForm(String username, String password, String captchaId, String captcha) { }
    public record RegisterForm(String username, String password, String accountType, String phone, String smsCode) { }
    public record RecoveryRequest(String username, String phone, String smsCode) { }
    public record RecoveryConfirm(String token, String newPassword) { }
}
