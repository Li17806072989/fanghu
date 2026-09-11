package com.fanghu.platform.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.SysUser;
import com.fanghu.platform.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, String> captchas = new ConcurrentHashMap<>();
    private final SysUserMapper users;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper users) {
        this.users = users;
    }

    @GetMapping("/captcha")
    public Map<String, String> captcha() {
        String id = UUID.randomUUID().toString();
        String code = String.valueOf(1000 + new Random().nextInt(9000));
        captchas.put(id, code);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='40'>"
            + "<rect width='100%' height='100%' fill='#eef3f5'/>"
            + "<path d='M0 13L120 29M0 31L120 9' stroke='#91a8b2'/>"
            + "<text x='18' y='28' font-size='24' font-family='Arial' fill='#173b4d'>"
            + code + "</text></svg>";
        return Map.of("captchaId", id, "imageBase64", "data:image/svg+xml;base64,"
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
        Long roleId = users.roleId(accountType);
        if (roleId == null) {
            throw new IllegalStateException("账号角色尚未初始化，请联系管理员");
        }
        SysUser user = new SysUser();
        user.setUserName(username);
        user.setPassword(passwords.encode(form.password()));
        user.setStatus("0");
        users.insert(user);
        users.assignRole(user.getUserId(), roleId);
        String typeName = accountType.equals("supplier") ? "供应商" : "客户";
        return Map.of("message", typeName + "账号创建成功，请登录");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginForm form) {
        String code = captchas.remove(form.captchaId());
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, form.username()));
        if (code == null || !code.equalsIgnoreCase(form.captcha()) || user == null
            || !"0".equals(user.getStatus()) || !passwords.matches(form.password(), user.getPassword())) {
            throw new IllegalArgumentException("用户名、密码或验证码错误");
        }
        List<String> roles = users.roles(user.getUserId());
        if (roles.isEmpty()) throw new IllegalArgumentException("账号尚未分配角色，请联系平台管理员");
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
    public record RegisterForm(String username, String password, String accountType) { }
}
