package org.dromara.fanghu.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.fanghu.domain.CustomerProfile;
import org.dromara.fanghu.domain.Demand;
import org.dromara.fanghu.domain.Lead;
import org.dromara.fanghu.domain.LeadEvent;
import org.dromara.fanghu.domain.Solution;
import org.dromara.fanghu.domain.SupplierCompany;
import org.dromara.system.domain.SysUser;
import org.dromara.fanghu.mapper.CustomerProfileMapper;
import org.dromara.fanghu.mapper.DemandMapper;
import org.dromara.fanghu.mapper.LeadEventMapper;
import org.dromara.fanghu.mapper.LeadMapper;
import org.dromara.fanghu.mapper.SolutionMapper;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.system.mapper.SysUserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadWorkflowService {
    private final DemandMapper demands;
    private final SupplierMapper suppliers;
    private final SolutionMapper solutions;
    private final LeadMapper leads;
    private final LeadEventMapper events;
    private final CustomerProfileMapper profiles;
    private final SysUserMapper users;

    public LeadWorkflowService(DemandMapper demands, SupplierMapper suppliers, SolutionMapper solutions,
                               LeadMapper leads, LeadEventMapper events, CustomerProfileMapper profiles,
                               SysUserMapper users) {
        this.demands = demands;
        this.suppliers = suppliers;
        this.solutions = solutions;
        this.leads = leads;
        this.events = events;
        this.profiles = profiles;
        this.users = users;
    }

    @Transactional
    public List<MatchResult> autoMatch(Long demandId) {
        Demand demand = required(demands.selectById(demandId), "需求");
        Map<Long, Integer> scores = scoreSuppliers(demand);
        List<MatchResult> ranked = rankedSuppliers(scores);
        for (MatchResult result : ranked) {
            if (leads.selectCount(new LambdaQueryWrapper<Lead>().eq(Lead::getDemandId, demandId).eq(Lead::getSupplierId, result.supplierId())) > 0) continue;
            Lead lead = new Lead();
            lead.setDemandId(demandId);
            lead.setCustomerId(demand.getCustomerId());
            lead.setSupplierId(result.supplierId());
            lead.setStatus("MATCHED");
            lead.setMatchScore(BigDecimal.valueOf(result.score()));
            lead.setCustomerConfirmation("PENDING");
            lead.setSupplierConfirmation("PENDING");
            leads.insert(lead);
            appendEvent(lead, "AUTO_MATCH", null, "MATCHED", "系统依据分类、标签和企业状态生成候选对接", 0L, "系统", "SYSTEM");
        }
        demand.setStatus(ranked.isEmpty() ? "NEW" : "MATCHED");
        demands.updateById(demand);
        return ranked;
    }

    @Transactional
    public void customerConfirmation(Long leadId, boolean accepted, String remark) {
        Lead lead = required(leads.selectById(leadId), "线索");
        CustomerProfile profile = ownCustomer();
        if (profile == null || !profile.getId().equals(lead.getCustomerId())) throw new IllegalArgumentException("无权确认该线索");
        requireOpenForConfirmation(lead, lead.getCustomerConfirmation());
        String before = lead.getStatus();
        lead.setCustomerConfirmation(accepted ? "ACCEPTED" : "DECLINED");
        lead.setCustomerConfirmedAt(LocalDateTime.now());
        if (!accepted) lead.setStatus("LOST");
        else synchronizeContactStatus(lead);
        lead.setRemark(blankToNull(remark));
        leads.updateById(lead);
        appendCurrentEvent(lead, accepted ? "CUSTOMER_ACCEPTED" : "CUSTOMER_DECLINED", before, remark, "CUSTOMER");
    }

    @Transactional
    public void supplierConfirmation(Long leadId, boolean accepted, String remark) {
        Lead lead = required(ownedSupplierLead(leadId), "线索");
        requireOpenForConfirmation(lead, lead.getSupplierConfirmation());
        String before = lead.getStatus();
        lead.setSupplierConfirmation(accepted ? "ACCEPTED" : "DECLINED");
        lead.setSupplierConfirmedAt(LocalDateTime.now());
        if (!accepted) lead.setStatus("LOST");
        else synchronizeContactStatus(lead);
        lead.setRemark(blankToNull(remark));
        leads.updateById(lead);
        appendCurrentEvent(lead, accepted ? "SUPPLIER_ACCEPTED" : "SUPPLIER_DECLINED", before, remark, "SUPPLIER");
    }

    @Transactional
    public void supplierFeedback(Long leadId, String rawStatus, String remark, LocalDateTime nextActionAt) {
        Lead lead = required(ownedSupplierLead(leadId), "线索");
        if (!contactReleased(lead)) throw new IllegalArgumentException("双方确认联系意向后，才能更新对接进展");
        String target = WorkflowStatus.lead(rawStatus);
        validateOperationalTransition(lead.getStatus(), target);
        String before = lead.getStatus();
        lead.setStatus(target);
        lead.setRemark(blankToNull(remark));
        lead.setNextActionAt(nextActionAt);
        leads.updateById(lead);
        appendCurrentEvent(lead, "SUPPLIER_FEEDBACK", before, remark, "SUPPLIER");
    }

    @Transactional
    public void operatorFollow(Long leadId, String rawStatus, String remark, LocalDateTime nextActionAt) {
        Lead lead = required(leads.selectById(leadId), "线索");
        String target = WorkflowStatus.lead(rawStatus);
        if ("CONTACTED".equals(target) && !contactReleased(lead)) throw new IllegalArgumentException("双方确认联系意向后才能标记为已联系");
        validateOperationalTransition(lead.getStatus(), target);
        String before = lead.getStatus();
        lead.setStatus(target);
        lead.setOperatorUserId(org.dromara.common.satoken.utils.LoginHelper.getUserId());
        lead.setRemark(blankToNull(remark));
        lead.setNextActionAt(nextActionAt);
        leads.updateById(lead);
        appendCurrentEvent(lead, "OPERATOR_FOLLOWUP", before, remark, "OPERATOR");
    }

    public List<LeadEvent> events(Long leadId) {
        return events.selectList(new LambdaQueryWrapper<LeadEvent>().eq(LeadEvent::getLeadId, leadId).orderByDesc(LeadEvent::getCreatedAt));
    }

    public boolean contactReleased(Lead lead) {
        return "ACCEPTED".equals(lead.getCustomerConfirmation()) && "ACCEPTED".equals(lead.getSupplierConfirmation());
    }

    public CustomerProfile ownCustomer() {
        return profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>().eq(CustomerProfile::getUserId, org.dromara.common.satoken.utils.LoginHelper.getUserId()));
    }

    private Map<Long, Integer> scoreSuppliers(Demand demand) {
        Map<Long, Integer> scores = new HashMap<>();
        String text = (demand.getTitle() + " " + demand.getDescription()).toLowerCase();
        for (Solution solution : solutions.selectList(new LambdaQueryWrapper<Solution>().eq(Solution::getAuditStatus, "APPROVED"))) {
            int score = Objects.equals(demand.getCategoryId(), solution.getCategoryId()) ? 60 : 10;
            if (solution.getSceneTags() != null) for (String tag : solution.getSceneTags().replaceAll("[\\[\\]\"]", "").split(",")) {
                if (!tag.isBlank() && text.contains(tag.trim().toLowerCase())) score += 10;
            }
            scores.merge(solution.getSupplierId(), Math.min(score, 80), Math::max);
        }
        return scores;
    }

    private List<MatchResult> rankedSuppliers(Map<Long, Integer> scores) {
        List<MatchResult> ranked = new ArrayList<>();
        for (SupplierCompany supplier : suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>().eq(SupplierCompany::getServiceStatus, "ACTIVE"))) {
            int score = scores.getOrDefault(supplier.getId(), 0);
            if ("APPROVED".equals(supplier.getVerificationStatus())) score += 10;
            if (supplier.getRatingScore() != null) score += Math.min(10, supplier.getRatingScore().intValue() / 10);
            if (score > 0) ranked.add(new MatchResult(supplier.getId(), supplier.getName(), score));
        }
        ranked.sort(Comparator.comparingInt(MatchResult::score).reversed());
        return ranked.stream().limit(5).toList();
    }

    private Lead ownedSupplierLead(Long leadId) {
        SupplierCompany supplier = suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>().eq(SupplierCompany::getOwnerUserId, org.dromara.common.satoken.utils.LoginHelper.getUserId()));
        if (supplier == null) return null;
        return leads.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getId, leadId).eq(Lead::getSupplierId, supplier.getId()));
    }

    private void synchronizeContactStatus(Lead lead) {
        if (contactReleased(lead)) lead.setStatus("CONTACTED");
    }

    private void requireOpenForConfirmation(Lead lead, String confirmation) {
        if (!"MATCHED".equals(lead.getStatus())) throw new IllegalArgumentException("当前线索已结束或已进入对接阶段，不能再次确认");
        if (!"PENDING".equals(confirmation)) throw new IllegalArgumentException("当前主体已完成该线索的联系意向确认");
    }

    private void validateOperationalTransition(String before, String after) {
        if (before.equals(after)) throw new IllegalArgumentException("线索已处于该状态");
        boolean allowed = switch (before) {
            case "MATCHED" -> "CONTACTED".equals(after) || "LOST".equals(after);
            case "CONTACTED" -> "INTENT".equals(after) || "LOST".equals(after);
            case "INTENT" -> "WON".equals(after) || "LOST".equals(after);
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("当前线索状态不允许该操作");
    }

    private void appendCurrentEvent(Lead lead, String action, String before, String remark, String role) {
        Long userId = org.dromara.common.satoken.utils.LoginHelper.getUserId();
        SysUser user = users.selectById(userId);
        appendEvent(lead, action, before, lead.getStatus(), blankToNull(remark), userId,
            user == null ? "未知用户" : user.getUserName(), role);
    }

    private void appendEvent(Lead lead, String action, String before, String after, String remark,
                             Long actorId, String actorName, String actorRole) {
        LeadEvent event = new LeadEvent();
        event.setLeadId(lead.getId()); event.setAction(action); event.setBeforeStatus(before); event.setAfterStatus(after);
        event.setRemark(blankToNull(remark)); event.setActorUserId(actorId); event.setActorName(actorName); event.setActorRole(actorRole);
        events.insert(event);
    }

    private static <T> T required(T value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不存在或无权访问");
        return value;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public record MatchResult(Long supplierId, String supplierName, int score) { }
}
