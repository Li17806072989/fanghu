package com.fanghu.platform.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fanghu.platform.domain.Article;
import com.fanghu.platform.domain.AuditRecord;
import com.fanghu.platform.domain.Solution;
import com.fanghu.platform.domain.SupplierCompany;
import com.fanghu.platform.domain.SysUser;
import com.fanghu.platform.domain.Topic;
import com.fanghu.platform.mapper.ArticleMapper;
import com.fanghu.platform.mapper.AuditRecordMapper;
import com.fanghu.platform.mapper.SolutionMapper;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.mapper.SysUserMapper;
import com.fanghu.platform.mapper.TopicMapper;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Set<String> TYPES = Set.of("TOPIC", "ARTICLE", "SUPPLIER", "SOLUTION");
    private static final Set<String> TARGET_STATUSES = Set.of("APPROVED", "REJECTED", "OFFLINE");

    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final SupplierMapper suppliers;
    private final SolutionMapper solutions;
    private final AuditRecordMapper records;
    private final SysUserMapper users;

    public AuditService(TopicMapper topics, ArticleMapper articles, SupplierMapper suppliers,
                        SolutionMapper solutions, AuditRecordMapper records, SysUserMapper users) {
        this.topics = topics;
        this.articles = articles;
        this.suppliers = suppliers;
        this.solutions = solutions;
        this.records = records;
        this.users = users;
    }

    @Transactional
    public void audit(String rawType, Long bizId, String rawStatus, String rawOpinion) {
        String type = normalizeType(rawType);
        String status = WorkflowStatus.audit(rawStatus == null ? "" : rawStatus.toUpperCase(Locale.ROOT));
        if (!TARGET_STATUSES.contains(status)) throw new IllegalArgumentException("审核操作仅支持通过、驳回或下线");
        String opinion = rawOpinion == null ? "" : rawOpinion.trim();
        if ("REJECTED".equals(status) && opinion.isEmpty()) throw new IllegalArgumentException("驳回时必须填写审核意见");
        if (opinion.length() > 1000) throw new IllegalArgumentException("审核意见不能超过1000字");

        String beforeStatus = currentStatus(type, bizId);
        validateTransition(beforeStatus, status);
        updateStatus(type, bizId, status);

        Long operatorId = StpUtil.getLoginIdAsLong();
        SysUser user = users.selectById(operatorId);
        AuditRecord record = new AuditRecord();
        record.setBizType(type);
        record.setBizId(bizId);
        record.setAction(status);
        record.setBeforeStatus(beforeStatus);
        record.setAfterStatus(status);
        record.setOpinion(opinion.isEmpty() ? null : opinion);
        record.setOperatorUserId(operatorId);
        record.setOperatorName(user == null ? "未知审核人" : user.getUserName());
        records.insert(record);
    }

    public String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("不支持的审核对象类型");
        return type;
    }

    private String currentStatus(String type, Long id) {
        return switch (type) {
            case "TOPIC" -> required(topics.selectById(id), "专题").getStatus();
            case "ARTICLE" -> required(articles.selectById(id), "资讯").getStatus();
            case "SUPPLIER" -> required(suppliers.selectById(id), "供应商").getVerificationStatus();
            case "SOLUTION" -> required(solutions.selectById(id), "解决方案").getAuditStatus();
            default -> throw new IllegalArgumentException("不支持的审核对象类型");
        };
    }

    private void updateStatus(String type, Long id, String status) {
        LocalDateTime now = LocalDateTime.now();
        switch (type) {
            case "TOPIC" -> {
                Topic item = topics.selectById(id);
                item.setStatus(status);
                if ("APPROVED".equals(status) && item.getPublishedAt() == null) item.setPublishedAt(now);
                topics.updateById(item);
            }
            case "ARTICLE" -> {
                Article item = articles.selectById(id);
                item.setStatus(status);
                if ("APPROVED".equals(status) && item.getPublishedAt() == null) item.setPublishedAt(now);
                articles.updateById(item);
            }
            case "SUPPLIER" -> {
                SupplierCompany item = suppliers.selectById(id);
                item.setVerificationStatus(status);
                suppliers.updateById(item);
            }
            case "SOLUTION" -> {
                Solution item = solutions.selectById(id);
                item.setAuditStatus(status);
                solutions.updateById(item);
            }
            default -> throw new IllegalArgumentException("不支持的审核对象类型");
        }
    }

    private void validateTransition(String before, String after) {
        if (after.equals(before)) throw new IllegalArgumentException("当前记录已处于该状态，无需重复审核");
        boolean allowed = switch (before) {
            case "DRAFT", "PENDING", "REJECTED" -> "APPROVED".equals(after) || "REJECTED".equals(after);
            case "APPROVED" -> "OFFLINE".equals(after);
            case "OFFLINE" -> "APPROVED".equals(after);
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("当前状态不允许执行该审核操作");
    }

    private static <T> T required(T value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不存在");
        return value;
    }
}
