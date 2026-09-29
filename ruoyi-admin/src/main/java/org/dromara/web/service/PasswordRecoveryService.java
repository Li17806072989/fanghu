package org.dromara.web.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.system.domain.SysUser;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {

    private static final String RESET_KEY = "fanghu:auth:password-reset:";
    private static final Duration RESET_TTL = Duration.ofMinutes(10);

    private final SysUserMapper userMapper;
    private final ISysUserService userService;
    private final SysRegisterService registerService;

    public String requestToken(String username, String contact, String code, String uuid) {
        registerService.validateCaptcha(username, code, uuid);
        SysUser user = userMapper.lambda()
            .eq(SysUser::getUserName, username)
            .and(q -> q.eq(SysUser::getPhoneNumber, contact).or().eq(SysUser::getEmail, contact))
            .one();
        if (user == null) {
            throw new ServiceException("账号或绑定联系方式不匹配");
        }
        String token = IdUtil.fastSimpleUUID();
        RedisUtils.setCacheObject(RESET_KEY + token, user.getUserId(), RESET_TTL);
        return token;
    }

    public void resetPassword(String token, String newPassword) {
        Long userId = RedisUtils.getCacheObject(RESET_KEY + token);
        if (userId == null) {
            throw new ServiceException("找回密码凭证已失效，请重新验证");
        }
        int rows = DataPermissionHelper.ignore(() -> userService.resetUserPwd(userId, BCrypt.hashpw(newPassword)));
        if (rows != 1) {
            throw new ServiceException("密码重置失败");
        }
        RedisUtils.deleteObject(RESET_KEY + token);
    }
}
