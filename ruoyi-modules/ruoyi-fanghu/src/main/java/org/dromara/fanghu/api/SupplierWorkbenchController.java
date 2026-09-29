package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.fanghu.domain.Demand;
import org.dromara.fanghu.domain.CustomerProfile;
import org.dromara.fanghu.domain.FileAsset;
import org.dromara.fanghu.domain.FileDownloadLog;
import org.dromara.fanghu.domain.Lead;
import org.dromara.fanghu.domain.Solution;
import org.dromara.fanghu.domain.SupplierCompany;
import org.dromara.fanghu.domain.SupplierMetric;
import org.dromara.fanghu.domain.SupplierMembership;
import org.dromara.fanghu.domain.LeadEvent;
import org.dromara.fanghu.mapper.DemandMapper;
import org.dromara.fanghu.mapper.CustomerProfileMapper;
import org.dromara.fanghu.mapper.FileAssetMapper;
import org.dromara.fanghu.mapper.FileDownloadLogMapper;
import org.dromara.fanghu.mapper.LeadMapper;
import org.dromara.fanghu.mapper.SolutionMapper;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.fanghu.mapper.SupplierMetricMapper;
import org.dromara.fanghu.mapper.SupplierMembershipMapper;
import org.dromara.fanghu.service.LeadWorkflowService;
import org.dromara.fanghu.service.TaxonomyService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckRole(value = {"supplier", "admin", "superadmin"}, mode = SaMode.OR)
@RestController
@RequestMapping("/api/workbench/supplier")
public class SupplierWorkbenchController {
    private final SupplierMapper suppliers;
    private final SolutionMapper solutions;
    private final FileAssetMapper files;
    private final LeadMapper leads;
    private final DemandMapper demands;
    private final SupplierMetricMapper metrics;
    private final SupplierMembershipMapper memberships;
    private final FileDownloadLogMapper downloadLogs;
    private final CustomerProfileMapper profiles;
    private final LeadWorkflowService leadWorkflow;
    private final TaxonomyService taxonomy;

    public SupplierWorkbenchController(SupplierMapper suppliers, SolutionMapper solutions,
                                       FileAssetMapper files, LeadMapper leads,
                                       DemandMapper demands, SupplierMetricMapper metrics,
                                       SupplierMembershipMapper memberships, FileDownloadLogMapper downloadLogs,
                                       CustomerProfileMapper profiles, LeadWorkflowService leadWorkflow,
                                       TaxonomyService taxonomy) {
        this.suppliers = suppliers;
        this.solutions = solutions;
        this.files = files;
        this.leads = leads;
        this.demands = demands;
        this.metrics = metrics;
        this.memberships = memberships;
        this.downloadLogs = downloadLogs;
        this.profiles = profiles;
        this.leadWorkflow = leadWorkflow;
        this.taxonomy = taxonomy;
    }

    @GetMapping
    public Overview overview() {
        SupplierCompany supplier = ownSupplier();
        if (supplier == null) {
            return new Overview(null, List.of(), List.of(), List.of(), Summary.empty(), List.of(), null, null);
        }

        List<Solution> solutionList = solutions.selectList(new LambdaQueryWrapper<Solution>()
            .eq(Solution::getSupplierId, supplier.getId()).orderByDesc(Solution::getUpdatedAt));
        List<Long> solutionIds = solutionList.stream().map(Solution::getId).toList();
        Map<Long, List<Long>> assignments = taxonomy.assignments("SOLUTION", solutionIds);
        solutionList.forEach(solution -> solution.setTaxonomyIds(assignments.getOrDefault(solution.getId(), List.of())));
        List<FileAsset> attachmentList = solutionIds.isEmpty() ? List.of() : files.selectList(
            new LambdaQueryWrapper<FileAsset>().eq(FileAsset::getOwnerType, "SOLUTION")
                .in(FileAsset::getOwnerId, solutionIds).orderByDesc(FileAsset::getCreatedAt));
        List<Lead> leadList = leads.selectList(new LambdaQueryWrapper<Lead>()
            .eq(Lead::getSupplierId, supplier.getId()).orderByDesc(Lead::getUpdatedAt));

        List<LeadRow> leadRows = new ArrayList<>();
        for (Lead lead : leadList) {
            Demand demand = demands.selectById(lead.getDemandId());
            CustomerProfile customer = leadWorkflow.contactReleased(lead) ? profiles.selectById(lead.getCustomerId()) : null;
            leadRows.add(new LeadRow(lead.getId(), lead.getDemandId(),
                demand == null ? "需求已删除" : demand.getTitle(), lead.getStatus(),
                lead.getMatchScore(), lead.getCustomerConfirmation(), lead.getSupplierConfirmation(),
                customer == null ? null : customer.getContactName(), customer == null ? null : customer.getContactPhone(),
                lead.getRemark(), lead.getNextActionAt(), lead.getUpdatedAt()));
        }

        List<SupplierMetric> metricRows = metrics.selectList(new LambdaQueryWrapper<SupplierMetric>()
            .eq(SupplierMetric::getSupplierId, supplier.getId())
            .ge(SupplierMetric::getMetricDate, LocalDate.now().minusDays(29))
            .orderByAsc(SupplierMetric::getMetricDate));
        long impressions = metricRows.stream().mapToLong(v -> value(v.getImpressions())).sum();
        long views = metricRows.stream().mapToLong(v -> value(v.getViews())).sum();
        long downloads = attachmentList.stream().mapToLong(v -> value(v.getDownloadCount())).sum();
        long inquiries = leadList.size();
        long qualified = leadList.stream().filter(v -> Set.of("INTENT", "WON").contains(v.getStatus())).count();
        long won = leadList.stream().filter(v -> "WON".equals(v.getStatus())).count();
        Summary summary = new Summary(impressions, views, downloads, inquiries, qualified, won);
        SupplierMembership membership = latestMembership(supplier.getId());
        ServiceEffect serviceEffect = serviceEffect(membership, attachmentList, leadList);
        return new Overview(supplier, solutionList, attachmentList, leadRows, summary, metricRows, membership, serviceEffect);
    }

    @PostMapping("/paid-service/apply")
    @Log(title = "供应商付费服务申请", businessType = BusinessType.INSERT)
    public SupplierMembership applyPaidService(@RequestBody(required = false) PaidServiceApplication form) {
        SupplierCompany supplier = requiredSupplier();
        if (!"APPROVED".equals(supplier.getVerificationStatus())) {
            throw new IllegalArgumentException("企业资质审核通过后才能申请付费投放");
        }
        long activeOrPending = memberships.selectCount(new LambdaQueryWrapper<SupplierMembership>()
            .eq(SupplierMembership::getSupplierId, supplier.getId())
            .and(query -> query.eq(SupplierMembership::getStatus, "PENDING")
                .or().eq(SupplierMembership::getStatus, "ACTIVE")
                    .and(active -> active.isNull(SupplierMembership::getEndsAt)
                        .or().gt(SupplierMembership::getEndsAt, LocalDateTime.now()))));
        if (activeOrPending > 0) throw new IllegalArgumentException("当前已有待处理或生效中的会员服务");
        SupplierMembership membership = new SupplierMembership();
        membership.setSupplierId(supplier.getId());
        membership.setSourceType("PAID");
        membership.setPlanName("付费投放服务");
        membership.setPaymentStatus("PENDING");
        membership.setStatus("PENDING");
        membership.setPlacementEnabled(false);
        membership.setNote(form == null ? null : limited(form.note(), 500));
        memberships.insert(membership);
        return membership;
    }

    @PutMapping("/profile")
    @Log(title = "供应商企业资料", businessType = BusinessType.UPDATE)
    public SupplierCompany updateProfile(@RequestBody ProfileForm form) {
        SupplierCompany supplier = requiredSupplier();
        if (form.name() == null || form.name().isBlank()) {
            throw new IllegalArgumentException("企业名称不能为空");
        }
        supplier.setName(form.name().trim());
        supplier.setCreditCode(blankToNull(form.creditCode()));
        supplier.setIntro(blankToNull(form.intro()));
        suppliers.updateById(supplier);
        return supplier;
    }

    @PatchMapping("/leads/{id}/feedback")
    @Log(title = "供应商线索反馈", businessType = BusinessType.UPDATE)
    public boolean feedback(@org.springframework.web.bind.annotation.PathVariable Long id,
                            @RequestBody LeadFeedback form) {
        requiredSupplier();
        leadWorkflow.supplierFeedback(id, form.status(), form.remark(), form.nextActionAt());
        return true;
    }

    @PatchMapping("/leads/{id}/confirmation")
    @Log(title = "供应商对接确认", businessType = BusinessType.UPDATE)
    public boolean confirmation(@org.springframework.web.bind.annotation.PathVariable Long id,
                                @RequestBody LeadConfirmation form) {
        requiredSupplier();
        if (form == null || form.accepted() == null) throw new IllegalArgumentException("请选择是否确认对接");
        leadWorkflow.supplierConfirmation(id, form.accepted(), form.remark());
        return true;
    }

    @GetMapping("/leads/{id}/events")
    public List<LeadEvent> events(@org.springframework.web.bind.annotation.PathVariable Long id) {
        requiredSupplier();
        Lead lead = leads.selectOne(new LambdaQueryWrapper<Lead>()
            .eq(Lead::getId, id).eq(Lead::getSupplierId, ownSupplier().getId()));
        if (lead == null) throw new IllegalArgumentException("线索不存在或无权访问");
        return leadWorkflow.events(id);
    }

    private SupplierCompany ownSupplier() {
        return suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>()
            .eq(SupplierCompany::getOwnerUserId, org.dromara.common.satoken.utils.LoginHelper.getUserId()));
    }

    private SupplierCompany requiredSupplier() {
        SupplierCompany supplier = ownSupplier();
        if (supplier == null) {
            throw new IllegalArgumentException("当前账号尚未完成供应商入驻");
        }
        return supplier;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SupplierMembership latestMembership(Long supplierId) {
        SupplierMembership membership = memberships.selectOne(new LambdaQueryWrapper<SupplierMembership>()
            .eq(SupplierMembership::getSupplierId, supplierId)
            .orderByDesc(SupplierMembership::getCreatedAt).last("LIMIT 1"));
        if (membership != null && "ACTIVE".equals(membership.getStatus()) && membership.getEndsAt() != null
            && membership.getEndsAt().isBefore(LocalDateTime.now())) {
            membership.setStatus("EXPIRED");
            membership.setPlacementEnabled(false);
            memberships.updateById(membership);
        }
        return membership;
    }

    private ServiceEffect serviceEffect(SupplierMembership membership, List<FileAsset> attachments, List<Lead> leadList) {
        if (membership == null || membership.getStartsAt() == null) return null;
        LocalDateTime start = membership.getStartsAt();
        LocalDateTime end = membership.getEndsAt() == null || membership.getEndsAt().isAfter(LocalDateTime.now())
            ? LocalDateTime.now() : membership.getEndsAt();
        if (end.isBefore(start)) return null;
        List<SupplierMetric> serviceMetrics = metrics.selectList(new LambdaQueryWrapper<SupplierMetric>()
            .eq(SupplierMetric::getSupplierId, membership.getSupplierId())
            .ge(SupplierMetric::getMetricDate, start.toLocalDate())
            .le(SupplierMetric::getMetricDate, end.toLocalDate()));
        long impressions = serviceMetrics.stream().mapToLong(item -> value(item.getImpressions())).sum();
        long views = serviceMetrics.stream().mapToLong(item -> value(item.getViews())).sum();
        List<Long> fileIds = attachments.stream().map(FileAsset::getId).toList();
        long downloads = fileIds.isEmpty() ? 0 : downloadLogs.selectCount(new LambdaQueryWrapper<FileDownloadLog>()
            .in(FileDownloadLog::getFileId, fileIds).eq(FileDownloadLog::getResult, "ISSUED")
            .ge(FileDownloadLog::getCreatedAt, start).le(FileDownloadLog::getCreatedAt, end));
        List<Lead> serviceLeads = leadList.stream()
            .filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(start) && !item.getCreatedAt().isAfter(end))
            .toList();
        long qualified = serviceLeads.stream().filter(item -> Set.of("INTENT", "WON").contains(item.getStatus())).count();
        long won = serviceLeads.stream().filter(item -> "WON".equals(item.getStatus())).count();
        return new ServiceEffect(start, end, impressions, views, downloads, serviceLeads.size(), qualified, won);
    }

    private static String limited(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("申请说明不能超过" + max + "字");
        return result;
    }

    public record Overview(SupplierCompany supplier, List<Solution> solutions,
                           List<FileAsset> attachments, List<LeadRow> leads,
                           Summary summary, List<SupplierMetric> trend,
                           SupplierMembership membership, ServiceEffect serviceEffect) { }
    public record Summary(long impressions, long views, long downloads,
                          long inquiries, long qualifiedLeads, long wonLeads) {
        static Summary empty() { return new Summary(0, 0, 0, 0, 0, 0); }
    }
    public record LeadRow(Long id, Long demandId, String demandTitle, String status,
                          java.math.BigDecimal matchScore, String customerConfirmation, String supplierConfirmation,
                          String contactName, String contactPhone, String remark,
                          LocalDateTime nextActionAt, LocalDateTime updatedAt) { }
    public record ProfileForm(String name, String creditCode, String intro) { }
    public record LeadFeedback(String status, String remark, LocalDateTime nextActionAt) { }
    public record LeadConfirmation(Boolean accepted, String remark) { }
    public record PaidServiceApplication(String note) { }
    public record ServiceEffect(LocalDateTime startsAt, LocalDateTime endsAt, long impressions, long views,
                                long downloads, long inquiries, long qualifiedLeads, long wonLeads) { }
}
