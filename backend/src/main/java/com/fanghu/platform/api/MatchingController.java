package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.CustomerProfile;
import com.fanghu.platform.domain.Demand;
import com.fanghu.platform.mapper.CustomerProfileMapper;
import com.fanghu.platform.mapper.DemandMapper;
import com.fanghu.platform.service.LeadWorkflowService;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {
    private final DemandMapper demands;
    private final CustomerProfileMapper profiles;
    private final LeadWorkflowService leadWorkflow;

    public MatchingController(DemandMapper demands, CustomerProfileMapper profiles, LeadWorkflowService leadWorkflow) {
        this.demands = demands;
        this.profiles = profiles;
        this.leadWorkflow = leadWorkflow;
    }

    @SaCheckRole(value = {"customer", "admin"}, mode = SaMode.OR)
    @PostMapping("/demands/{id}/auto")
    public List<LeadWorkflowService.MatchResult> match(@PathVariable Long id) {
        Demand demand = Optional.ofNullable(demands.selectById(id)).orElseThrow(() -> new IllegalArgumentException("需求不存在"));
        ensureAccess(demand);
        return leadWorkflow.autoMatch(id);
    }

    private void ensureAccess(Demand demand) {
        if (StpUtil.hasRole("admin")) return;
        Long userId = StpUtil.getLoginIdAsLong();
        CustomerProfile profile = profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>().eq(CustomerProfile::getUserId, userId));
        boolean customerOwner = profile != null && profile.getId().equals(demand.getCustomerId());
        if (!customerOwner) throw new IllegalArgumentException("仅需求所属客户或平台管理员可执行匹配");
    }
}
