package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fanghu.platform.domain.CustomerProfile;
import com.fanghu.platform.domain.Demand;
import com.fanghu.platform.domain.Lead;
import com.fanghu.platform.domain.LeadEvent;
import com.fanghu.platform.domain.SupplierCompany;
import com.fanghu.platform.domain.SysUser;
import com.fanghu.platform.mapper.CustomerProfileMapper;
import com.fanghu.platform.mapper.DemandMapper;
import com.fanghu.platform.mapper.LeadMapper;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.mapper.SysUserMapper;
import com.fanghu.platform.service.LeadWorkflowService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/leads")
@SaCheckPermission("platform:write")
public class LeadOperationsController {
    private final LeadMapper leads;
    private final DemandMapper demands;
    private final CustomerProfileMapper customers;
    private final SupplierMapper suppliers;
    private final SysUserMapper users;
    private final LeadWorkflowService leadWorkflow;

    public LeadOperationsController(LeadMapper leads, DemandMapper demands, CustomerProfileMapper customers,
                                    SupplierMapper suppliers, SysUserMapper users, LeadWorkflowService leadWorkflow) {
        this.leads = leads; this.demands = demands; this.customers = customers;
        this.suppliers = suppliers; this.users = users; this.leadWorkflow = leadWorkflow;
    }

    @GetMapping
    public LeadPage list(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "1") long page,
                         @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<Lead> query = new LambdaQueryWrapper<Lead>()
            .eq(status != null && !status.isBlank(), Lead::getStatus, status)
            .orderByDesc(Lead::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            List<Long> demandIds = demands.selectList(new LambdaQueryWrapper<Demand>().like(Demand::getTitle, term))
                .stream().map(Demand::getId).toList();
            List<Long> customerIds = customers.selectList(new LambdaQueryWrapper<CustomerProfile>()
                .like(CustomerProfile::getOrganization, term)).stream().map(CustomerProfile::getId).toList();
            List<Long> supplierIds = suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>()
                .like(SupplierCompany::getName, term)).stream().map(SupplierCompany::getId).toList();
            if (demandIds.isEmpty() && customerIds.isEmpty() && supplierIds.isEmpty()) {
                query.eq(Lead::getId, -1L);
            } else {
                query.and(q -> q.in(!demandIds.isEmpty(), Lead::getDemandId, demandIds)
                    .or().in(!customerIds.isEmpty(), Lead::getCustomerId, customerIds)
                    .or().in(!supplierIds.isEmpty(), Lead::getSupplierId, supplierIds));
            }
        }
        IPage<Lead> result = leads.selectPage(Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), query);
        List<LeadRow> rows = result.getRecords().stream().map(this::row).toList();
        return new LeadPage(result.getCurrent(), result.getSize(), result.getTotal(), rows);
    }

    @PatchMapping("/{id}/follow")
    public boolean follow(@PathVariable Long id, @RequestBody FollowForm form) {
        if (form == null || form.status() == null || form.status().isBlank()) throw new IllegalArgumentException("请选择线索进展");
        leadWorkflow.operatorFollow(id, form.status(), form.remark(), form.nextActionAt());
        return true;
    }

    @GetMapping("/{id}/events")
    public List<LeadEvent> events(@PathVariable Long id) {
        if (leads.selectById(id) == null) throw new IllegalArgumentException("线索不存在");
        return leadWorkflow.events(id);
    }

    private LeadRow row(Lead lead) {
        Demand demand = demands.selectById(lead.getDemandId());
        CustomerProfile customer = customers.selectById(lead.getCustomerId());
        SupplierCompany supplier = suppliers.selectById(lead.getSupplierId());
        String demandTitle = demand == null ? "需求已删除" : demand.getTitle();
        String organization = customer == null || customer.getOrganization() == null ? "客户资料待补充" : customer.getOrganization();
        String supplierName = supplier == null ? "供应商资料待补充" : supplier.getName();
        SysUser operator = lead.getOperatorUserId() == null ? null : users.selectById(lead.getOperatorUserId());
        return new LeadRow(lead.getId(), demandTitle, organization, supplierName, lead.getStatus(), lead.getMatchScore(),
            lead.getCustomerConfirmation(), lead.getSupplierConfirmation(), operator == null ? null : operator.getUserName(),
            lead.getRemark(), lead.getNextActionAt(), lead.getUpdatedAt());
    }

    public record LeadPage(long current, long size, long total, List<LeadRow> records) { }
    public record LeadRow(Long id, String demandTitle, String customerOrganization, String supplierName, String status,
                          BigDecimal matchScore, String customerConfirmation, String supplierConfirmation,
                          String operatorName, String remark, LocalDateTime nextActionAt, LocalDateTime updatedAt) { }
    public record FollowForm(String status, String remark, LocalDateTime nextActionAt) { }
}
