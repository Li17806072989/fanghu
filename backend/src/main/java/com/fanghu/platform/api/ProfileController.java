package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.CustomerProfile;
import com.fanghu.platform.mapper.CustomerProfileMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckRole(value = {"customer", "admin"}, mode = SaMode.OR)
@RestController
@RequestMapping("/api/me/profile")
public class ProfileController {
    private final CustomerProfileMapper profiles;

    public ProfileController(CustomerProfileMapper profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public CustomerProfile current() {
        return profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>()
            .eq(CustomerProfile::getUserId, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping
    public CustomerProfile save(@RequestBody CustomerProfile value) {
        Long userId = StpUtil.getLoginIdAsLong();
        CustomerProfile existing = profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>()
            .eq(CustomerProfile::getUserId, userId));
        if (existing == null) {
            value.setId(null);
            value.setUserId(userId);
            value.setVerificationStatus("UNVERIFIED");
            profiles.insert(value);
            return value;
        }
        existing.setOrganization(value.getOrganization());
        existing.setContactName(value.getContactName());
        existing.setContactPhone(value.getContactPhone());
        profiles.updateById(existing);
        return existing;
    }
}
