package com.fanghu.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.SysUser;
import com.fanghu.platform.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final SysUserMapper users;
    private final JdbcTemplate jdbc;
    private final String bootstrapPassword;

    public AdminBootstrap(SysUserMapper users, JdbcTemplate jdbc,
                          @Value("${FANGHU_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
        this.users = users;
        this.jdbc = jdbc;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(String... args) {
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, "admin"));
        if (user == null) {
            if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
                throw new IllegalStateException("FANGHU_BOOTSTRAP_ADMIN_PASSWORD must be set before first startup");
            }
            user = new SysUser();
            user.setUserName("admin");
            user.setPassword(new BCryptPasswordEncoder().encode(bootstrapPassword));
            user.setStatus("0");
            users.insert(user);
        }
        jdbc.update("INSERT IGNORE INTO sys_role(role_id,role_name,role_key,status) VALUES(1,'平台管理员','admin','0')");
        String[] permissions = {"platform:read", "platform:write", "platform:audit"};
        for (int i = 0; i < permissions.length; i++) {
            long menuId = 9001L + i;
            jdbc.update("INSERT IGNORE INTO sys_menu(menu_id,menu_name,perms,path,menu_type,visible) VALUES(?,?,?,?,'F','0')",
                menuId, permissions[i], permissions[i], "#");
            jdbc.update("INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES(1,?)", menuId);
        }
        jdbc.update("INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES(?,1)", user.getUserId());
    }
}
