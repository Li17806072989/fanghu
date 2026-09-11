package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.Solution;
import com.fanghu.platform.mapper.SolutionMapper;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.mapper.SupplierMetricMapper;
import com.fanghu.platform.mapper.SupplierMembershipMapper;
import com.fanghu.platform.domain.SupplierCompany;
import com.fanghu.platform.domain.SupplierMembership;
import cn.dev33.satoken.stp.StpUtil;
import com.fanghu.platform.service.WorkflowStatus;
import com.fanghu.platform.service.AuditService;
import com.fanghu.platform.service.TaxonomyService;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/solutions")
public class SolutionController {
    private final SolutionMapper solutions;
    private final SupplierMapper suppliers;
    private final SupplierMetricMapper metrics;
    private final SupplierMembershipMapper memberships;
    private final AuditService auditService;
    private final TaxonomyService taxonomy;

    public SolutionController(SolutionMapper solutions, SupplierMapper suppliers, SupplierMetricMapper metrics,
                              SupplierMembershipMapper memberships,
                              AuditService auditService, TaxonomyService taxonomy) {
        this.solutions = solutions;
        this.suppliers = suppliers;
        this.metrics = metrics;
        this.memberships = memberships;
        this.auditService = auditService;
        this.taxonomy = taxonomy;
    }

    @GetMapping
    public List<Solution> list(@RequestParam(required = false) Long categoryId) {
        List<Solution> result = solutions.selectList(new LambdaQueryWrapper<Solution>()
            .eq(categoryId != null, Solution::getCategoryId, categoryId)
            .eq(Solution::getAuditStatus, "APPROVED")
            .orderByDesc(Solution::getCreatedAt));
        List<Long> promotedSupplierIds = promotedSupplierIds();
        result.forEach(item -> item.setPromoted(promotedSupplierIds.contains(item.getSupplierId())));
        result.sort(java.util.Comparator.comparing((Solution item) -> Boolean.TRUE.equals(item.getPromoted())).reversed());
        result.stream().map(Solution::getSupplierId).distinct().forEach(id ->
            metrics.addImpressions(id, (int) result.stream().filter(v -> id.equals(v.getSupplierId())).count()));
        return result;
    }

    @SaCheckRole(value = {"supplier", "admin"}, mode = SaMode.OR)
    @GetMapping("/manage")
    public List<Solution> manageList(@RequestParam(required = false) String auditStatus) {
        String status = auditStatus == null || auditStatus.isBlank() ? null : WorkflowStatus.audit(auditStatus);
        LambdaQueryWrapper<Solution> query = new LambdaQueryWrapper<Solution>()
            .eq(status != null, Solution::getAuditStatus, status).orderByDesc(Solution::getCreatedAt);
        if (!StpUtil.hasRole("admin")) {
            List<Long> ids = suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>()
                .eq(SupplierCompany::getOwnerUserId, StpUtil.getLoginIdAsLong())).stream().map(SupplierCompany::getId).toList();
            if (ids.isEmpty()) return List.of();
            query.in(Solution::getSupplierId, ids);
        }
        return solutions.selectList(query);
    }

    @SaCheckRole(value = {"supplier", "admin"}, mode = SaMode.OR)
    @PostMapping
    @Transactional
    public Solution create(@RequestBody Solution value) {
        if (value.getTitle() == null || value.getTitle().isBlank() || value.getCategoryId() == null) {
            throw new IllegalArgumentException("方案标题和装备分类不能为空");
        }
        if (!StpUtil.hasRole("admin")) {
            SupplierCompany owned = suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>()
                .eq(SupplierCompany::getOwnerUserId, StpUtil.getLoginIdAsLong()));
            if (owned == null) throw new IllegalArgumentException("请先完成供应商入驻");
            value.setSupplierId(owned.getId());
        } else if (value.getSupplierId() == null) {
            throw new IllegalArgumentException("供应商不能为空");
        }
        value.setAuditStatus("PENDING");
        value.setVisibility("AUTHORIZED");
        solutions.insert(value);
        taxonomy.sync("SOLUTION", value.getId(), value.getCategoryId(), value.getTaxonomyIds());
        return value;
    }

    @SaCheckRole(value = {"supplier", "admin"}, mode = SaMode.OR)
    @PutMapping("/{id}")
    @Transactional
    public Solution update(@PathVariable Long id, @RequestBody Solution form) {
        Solution value = solutions.selectById(id);
        if (value == null) {
            throw new IllegalArgumentException("解决方案不存在");
        }
        ensureOwner(value);
        if (form.getTitle() == null || form.getTitle().isBlank() || form.getCategoryId() == null) {
            throw new IllegalArgumentException("方案标题和装备分类不能为空");
        }
        value.setTitle(form.getTitle().trim());
        value.setCategoryId(form.getCategoryId());
        value.setSceneTags(form.getSceneTags());
        value.setSummary(form.getSummary());
        value.setContent(form.getContent());
        value.setAuditStatus("PENDING");
        solutions.updateById(value);
        taxonomy.sync("SOLUTION", value.getId(), value.getCategoryId(), form.getTaxonomyIds());
        return value;
    }

    @SaCheckPermission("platform:audit")
    @PatchMapping("/{id}/audit")
    public boolean audit(@PathVariable Long id, @RequestParam String status) {
        auditService.audit("SOLUTION", id, status, null);
        return true;
    }

    private void ensureOwner(Solution solution) {
        if (StpUtil.hasRole("admin")) return;
        SupplierCompany supplier = suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>()
            .eq(SupplierCompany::getId, solution.getSupplierId())
            .eq(SupplierCompany::getOwnerUserId, StpUtil.getLoginIdAsLong()));
        if (supplier == null) throw new IllegalArgumentException("无权修改该解决方案");
    }

    private List<Long> promotedSupplierIds() {
        LocalDateTime now = LocalDateTime.now();
        return memberships.selectList(new LambdaQueryWrapper<SupplierMembership>()
                .eq(SupplierMembership::getStatus, "ACTIVE")
                .eq(SupplierMembership::getPlacementEnabled, true)
                .le(SupplierMembership::getStartsAt, now)
                .gt(SupplierMembership::getEndsAt, now))
            .stream().map(SupplierMembership::getSupplierId).distinct().toList();
    }
}
