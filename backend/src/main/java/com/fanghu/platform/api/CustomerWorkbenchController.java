package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.CustomerProfile;
import com.fanghu.platform.domain.Demand;
import com.fanghu.platform.domain.Lead;
import com.fanghu.platform.domain.LeadEvent;
import com.fanghu.platform.domain.SupplierCompany;
import com.fanghu.platform.mapper.CustomerProfileMapper;
import com.fanghu.platform.mapper.DemandMapper;
import com.fanghu.platform.mapper.LeadMapper;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.service.LeadWorkflowService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckRole(value = {"customer", "admin"}, mode = SaMode.OR)
@RestController
@RequestMapping("/api/workbench/customer")
public class CustomerWorkbenchController {
    private final CustomerProfileMapper profiles;
    private final DemandMapper demands;
    private final LeadMapper leads;
    private final SupplierMapper suppliers;
    private final LeadWorkflowService leadWorkflow;

    public CustomerWorkbenchController(CustomerProfileMapper profiles, DemandMapper demands, LeadMapper leads,
                                       SupplierMapper suppliers, LeadWorkflowService leadWorkflow) {
        this.profiles = profiles; this.demands = demands; this.leads = leads;
        this.suppliers = suppliers; this.leadWorkflow = leadWorkflow;
    }

    @GetMapping
    public Overview overview() {
        CustomerProfile customer = leadWorkflow.ownCustomer();
        if (customer == null) return new Overview(null, List.of(), List.of());
        List<Demand> demandList = demands.selectList(new LambdaQueryWrapper<Demand>()
            .eq(Demand::getCustomerId, customer.getId()).orderByDesc(Demand::getUpdatedAt));
        List<LeadRow> leadRows = leads.selectList(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getCustomerId, customer.getId()).orderByDesc(Lead::getUpdatedAt))
            .stream().map(this::leadRow).toList();
        return new Overview(customer, demandList, leadRows);
    }

    @PatchMapping("/leads/{id}/confirmation")
    public boolean confirmation(@PathVariable Long id, @RequestBody LeadConfirmation form) {
        if (form == null || form.accepted() == null) throw new IllegalArgumentException("请选择是否确认对接");
        leadWorkflow.customerConfirmation(id, form.accepted(), form.remark());
        return true;
    }

    @GetMapping("/leads/{id}/events")
    public List<LeadEvent> events(@PathVariable Long id) {
        Lead lead = leads.selectById(id);
        CustomerProfile customer = leadWorkflow.ownCustomer();
        if (lead == null || customer == null || !customer.getId().equals(lead.getCustomerId())) {
            throw new IllegalArgumentException("线索不存在或无权访问");
        }
        return leadWorkflow.events(id);
    }

    private LeadRow leadRow(Lead lead) {
        Demand demand = demands.selectById(lead.getDemandId());
        SupplierCompany supplier = suppliers.selectById(lead.getSupplierId());
        return new LeadRow(lead.getId(), lead.getDemandId(), demand == null ? "需求已删除" : demand.getTitle(),
            supplier == null ? "供应商资料待补充" : supplier.getName(), lead.getStatus(), lead.getMatchScore(),
            lead.getCustomerConfirmation(), lead.getSupplierConfirmation(), lead.getRemark(), lead.getNextActionAt(), lead.getUpdatedAt());
    }

    public record Overview(CustomerProfile customer, List<Demand> demands, List<LeadRow> leads) { }
    public record LeadRow(Long id, Long demandId, String demandTitle, String supplierName, String status,
                          BigDecimal matchScore, String customerConfirmation, String supplierConfirmation,
                          String remark, LocalDateTime nextActionAt, LocalDateTime updatedAt) { }
    public record LeadConfirmation(Boolean accepted, String remark) { }
}
