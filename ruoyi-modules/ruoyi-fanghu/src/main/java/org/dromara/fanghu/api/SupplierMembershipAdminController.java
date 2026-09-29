package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.fanghu.domain.SupplierCompany;
import org.dromara.fanghu.domain.SupplierMembership;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.fanghu.mapper.SupplierMembershipMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/supplier-memberships")
@SaCheckPermission("platform:write")
public class SupplierMembershipAdminController {
    private static final Set<String> STATUSES = Set.of("PENDING", "ACTIVE", "EXPIRED", "CANCELLED");
    private final SupplierMembershipMapper memberships;
    private final SupplierMapper suppliers;

    public SupplierMembershipAdminController(SupplierMembershipMapper memberships, SupplierMapper suppliers) {
        this.memberships = memberships;
        this.suppliers = suppliers;
    }

    @GetMapping
    public MembershipPage list(@RequestParam(defaultValue = "1") long page,
                               @RequestParam(defaultValue = "20") long size,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String status) {
        expireEndedMemberships();
        List<SupplierCompany> supplierList = suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>()
            .like(hasText(keyword), SupplierCompany::getName, keyword == null ? null : keyword.trim()));
        if (hasText(keyword) && supplierList.isEmpty()) return new MembershipPage(1, size, 0, List.of());
        List<Long> supplierIds = supplierList.stream().map(SupplierCompany::getId).toList();
        String normalizedStatus = normalizeStatus(status);
        IPage<SupplierMembership> result = memberships.selectPage(
            Page.of(Math.max(1, page), Math.min(100, Math.max(1, size))),
            new LambdaQueryWrapper<SupplierMembership>()
                .in(hasText(keyword), SupplierMembership::getSupplierId, supplierIds)
                .eq(normalizedStatus != null, SupplierMembership::getStatus, normalizedStatus)
                .orderByDesc(SupplierMembership::getCreatedAt));
        if (result.getRecords().isEmpty()) {
            return new MembershipPage(result.getCurrent(), result.getSize(), result.getTotal(), List.of());
        }
        Map<Long, SupplierCompany> supplierMap = suppliers.selectBatchIds(result.getRecords().stream()
                .map(SupplierMembership::getSupplierId).distinct().toList()).stream()
            .collect(Collectors.toMap(SupplierCompany::getId, Function.identity()));
        List<MembershipRow> rows = result.getRecords().stream()
            .map(item -> toRow(item, supplierMap.get(item.getSupplierId()))).toList();
        return new MembershipPage(result.getCurrent(), result.getSize(), result.getTotal(), rows);
    }

    @GetMapping("/suppliers")
    public List<SupplierOption> supplierOptions(@RequestParam(required = false) String keyword) {
        return suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>()
                .eq(SupplierCompany::getVerificationStatus, "APPROVED")
                .like(hasText(keyword), SupplierCompany::getName, keyword == null ? null : keyword.trim())
                .orderByAsc(SupplierCompany::getName).last("LIMIT 200"))
            .stream().map(item -> new SupplierOption(item.getId(), item.getName(), item.getCreditCode())).toList();
    }

    @PostMapping("/invite")
    public MembershipRow invite(@RequestBody InviteForm form) {
        if (form == null || form.supplierId() == null) throw new IllegalArgumentException("请选择受邀企业");
        SupplierCompany supplier = requiredSupplier(form.supplierId());
        if (!"APPROVED".equals(supplier.getVerificationStatus())) throw new IllegalArgumentException("仅已通过资质审核的企业可以获赠会员");
        ensureNoEffectiveMembership(supplier.getId(), null);
        int months = form.durationMonths() == null ? 12 : form.durationMonths();
        if (months < 1 || months > 60) throw new IllegalArgumentException("赠送期限需为1至60个月");
        LocalDateTime startsAt = LocalDateTime.now();
        SupplierMembership membership = new SupplierMembership();
        membership.setSupplierId(supplier.getId());
        membership.setSourceType("INVITED");
        membership.setPlanName(trim(form.planName(), 120, "邀请会员"));
        membership.setFeeAmount(BigDecimal.ZERO);
        membership.setPaymentStatus("NOT_REQUIRED");
        membership.setStatus("ACTIVE");
        membership.setPlacementEnabled(true);
        membership.setStartsAt(startsAt);
        membership.setEndsAt(startsAt.plusMonths(months));
        membership.setOperatorUserId(org.dromara.common.satoken.utils.LoginHelper.getUserId());
        membership.setNote(trim(form.note(), 500, null));
        memberships.insert(membership);
        return toRow(membership, supplier);
    }

    @PatchMapping("/{id}/activate")
    public MembershipRow activatePaid(@PathVariable Long id, @RequestBody ActivateForm form) {
        SupplierMembership membership = requiredMembership(id);
        if (!"PAID".equals(membership.getSourceType()) || !"PENDING".equals(membership.getStatus())) {
            throw new IllegalArgumentException("仅待处理的付费投放申请可以确认收款");
        }
        if (form == null || form.feeAmount() == null || form.feeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("请输入实际到账金额");
        }
        LocalDateTime startsAt = form.startsAt() == null ? LocalDateTime.now() : form.startsAt();
        if (form.endsAt() == null || !form.endsAt().isAfter(startsAt)) throw new IllegalArgumentException("服务结束时间必须晚于开始时间");
        ensureNoEffectiveMembership(membership.getSupplierId(), membership.getId());
        membership.setPlanName(trim(form.planName(), 120, "付费投放服务"));
        membership.setFeeAmount(form.feeAmount());
        membership.setPaymentStatus("PAID");
        membership.setStatus("ACTIVE");
        membership.setPlacementEnabled(true);
        membership.setStartsAt(startsAt);
        membership.setEndsAt(form.endsAt());
        membership.setOperatorUserId(org.dromara.common.satoken.utils.LoginHelper.getUserId());
        membership.setNote(trim(form.note(), 500, membership.getNote()));
        memberships.updateById(membership);
        return toRow(membership, requiredSupplier(membership.getSupplierId()));
    }

    @PatchMapping("/{id}/cancel")
    public MembershipRow cancel(@PathVariable Long id, @RequestBody(required = false) CancelForm form) {
        SupplierMembership membership = requiredMembership(id);
        if (Set.of("EXPIRED", "CANCELLED").contains(membership.getStatus())) throw new IllegalArgumentException("该服务已经结束");
        membership.setStatus("CANCELLED");
        membership.setPlacementEnabled(false);
        membership.setOperatorUserId(org.dromara.common.satoken.utils.LoginHelper.getUserId());
        if (form != null && hasText(form.note())) membership.setNote(trim(form.note(), 500, membership.getNote()));
        memberships.updateById(membership);
        return toRow(membership, requiredSupplier(membership.getSupplierId()));
    }

    private void expireEndedMemberships() {
        List<SupplierMembership> ended = memberships.selectList(new LambdaQueryWrapper<SupplierMembership>()
            .eq(SupplierMembership::getStatus, "ACTIVE").lt(SupplierMembership::getEndsAt, LocalDateTime.now()));
        for (SupplierMembership membership : ended) {
            membership.setStatus("EXPIRED");
            membership.setPlacementEnabled(false);
            memberships.updateById(membership);
        }
    }

    private void ensureNoEffectiveMembership(Long supplierId, Long ignoredId) {
        long count = memberships.selectCount(new LambdaQueryWrapper<SupplierMembership>()
            .eq(SupplierMembership::getSupplierId, supplierId)
            .ne(ignoredId != null, SupplierMembership::getId, ignoredId)
            .and(query -> query.eq(SupplierMembership::getStatus, "PENDING")
                .or().eq(SupplierMembership::getStatus, "ACTIVE")
                    .and(active -> active.isNull(SupplierMembership::getEndsAt)
                        .or().gt(SupplierMembership::getEndsAt, LocalDateTime.now()))));
        if (count > 0) throw new IllegalArgumentException("该企业已有待处理或生效中的会员服务");
    }

    private SupplierCompany requiredSupplier(Long id) {
        SupplierCompany supplier = suppliers.selectById(id);
        if (supplier == null) throw new IllegalArgumentException("供应商不存在");
        return supplier;
    }

    private SupplierMembership requiredMembership(Long id) {
        SupplierMembership membership = memberships.selectById(id);
        if (membership == null) throw new IllegalArgumentException("会员服务记录不存在");
        return membership;
    }

    private MembershipRow toRow(SupplierMembership item, SupplierCompany supplier) {
        return new MembershipRow(item.getId(), item.getSupplierId(), supplier == null ? "供应商已删除" : supplier.getName(),
            item.getSourceType(), item.getPlanName(), item.getFeeAmount(), item.getPaymentStatus(), item.getStatus(),
            item.getPlacementEnabled(), item.getStartsAt(), item.getEndsAt(), item.getNote(), item.getCreatedAt());
    }

    private String normalizeStatus(String value) {
        if (!hasText(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("会员状态不正确");
        return normalized;
    }

    private String trim(String value, int max, String fallback) {
        String result = hasText(value) ? value.trim() : fallback;
        if (result != null && result.length() > max) throw new IllegalArgumentException("填写内容过长");
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record InviteForm(Long supplierId, Integer durationMonths, String planName, String note) { }
    public record ActivateForm(String planName, BigDecimal feeAmount, LocalDateTime startsAt, LocalDateTime endsAt, String note) { }
    public record CancelForm(String note) { }
    public record SupplierOption(Long id, String name, String creditCode) { }
    public record MembershipRow(Long id, Long supplierId, String supplierName, String sourceType, String planName,
                                BigDecimal feeAmount, String paymentStatus, String status, Boolean placementEnabled,
                                LocalDateTime startsAt, LocalDateTime endsAt, String note, LocalDateTime createdAt) { }
    public record MembershipPage(long current, long size, long total, List<MembershipRow> records) { }
}
