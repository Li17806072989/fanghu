package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.fanghu.domain.Article;
import org.dromara.fanghu.domain.AuditRecord;
import org.dromara.fanghu.domain.Solution;
import org.dromara.fanghu.domain.SupplierCompany;
import org.dromara.system.domain.SysUser;
import org.dromara.fanghu.domain.Topic;
import org.dromara.fanghu.mapper.ArticleMapper;
import org.dromara.fanghu.mapper.AuditRecordMapper;
import org.dromara.fanghu.mapper.SolutionMapper;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.fanghu.mapper.TopicMapper;
import org.dromara.fanghu.service.AuditService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audits")
@SaCheckPermission("platform:audit")
public class AuditController {
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final SupplierMapper suppliers;
    private final SolutionMapper solutions;
    private final AuditRecordMapper records;
    private final SysUserMapper users;
    private final AuditService auditService;

    public AuditController(TopicMapper topics, ArticleMapper articles, SupplierMapper suppliers,
                           SolutionMapper solutions, AuditRecordMapper records, SysUserMapper users,
                           AuditService auditService) {
        this.topics = topics;
        this.articles = articles;
        this.suppliers = suppliers;
        this.solutions = solutions;
        this.records = records;
        this.users = users;
        this.auditService = auditService;
    }

    @GetMapping("/queue")
    public AuditPage<AuditQueueItem> queue(@RequestParam String bizType,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) LocalDate startDate,
                                            @RequestParam(required = false) LocalDate endDate,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        String type = auditService.normalizeType(bizType);
        Page<?> request = Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.atTime(LocalTime.MAX);
        return switch (type) {
            case "TOPIC" -> topicPage((Page<Topic>) request, status, keyword, start, end);
            case "ARTICLE" -> articlePage((Page<Article>) request, status, keyword, start, end);
            case "SUPPLIER" -> supplierPage((Page<SupplierCompany>) request, status, keyword, start, end);
            case "SOLUTION" -> solutionPage((Page<Solution>) request, status, keyword, start, end);
            default -> throw new IllegalArgumentException("不支持的审核对象类型");
        };
    }

    @PatchMapping("/{bizType}/{bizId}")
    @Log(title = "平台审核", businessType = BusinessType.UPDATE)
    public void audit(@PathVariable String bizType, @PathVariable Long bizId, @RequestBody AuditRequest request) {
        if (request == null) throw new IllegalArgumentException("请提交审核结果");
        auditService.audit(bizType, bizId, request.status(), request.opinion());
    }

    @GetMapping("/{bizType}/{bizId}/history")
    public List<AuditRecord> history(@PathVariable String bizType, @PathVariable Long bizId) {
        return records.selectList(new LambdaQueryWrapper<AuditRecord>()
            .eq(AuditRecord::getBizType, auditService.normalizeType(bizType))
            .eq(AuditRecord::getBizId, bizId)
            .orderByDesc(AuditRecord::getCreatedAt));
    }

    @GetMapping("/{bizType}/{bizId}")
    public AuditDetail detail(@PathVariable String bizType, @PathVariable Long bizId) {
        String type = auditService.normalizeType(bizType);
        return switch (type) {
            case "TOPIC" -> {
                Topic item = required(topics.selectById(bizId), "专题");
                yield new AuditDetail(new AuditQueueItem(item.getId(), type, item.getTitle(), item.getStatus(),
                    item.getSourceDesc(), "专题分类 #" + item.getCategoryId(), item.getCreatedAt(), item.getUpdatedAt()),
                    item.getSummary(), null, item.getCoverUrl());
            }
            case "ARTICLE" -> {
                Article item = required(articles.selectById(bizId), "资讯");
                yield new AuditDetail(new AuditQueueItem(item.getId(), type, item.getTitle(), item.getStatus(),
                    item.getSourceName(), "资讯分类 #" + item.getCategoryId(), item.getCreatedAt(), item.getUpdatedAt()),
                    item.getSummary(), item.getContent(), item.getSourceUrl());
            }
            case "SUPPLIER" -> {
                SupplierCompany item = required(suppliers.selectById(bizId), "供应商");
                String owner = userNames(List.of(item.getOwnerUserId())).getOrDefault(item.getOwnerUserId(), "供应商账号");
                yield new AuditDetail(new AuditQueueItem(item.getId(), type, item.getName(), item.getVerificationStatus(),
                    owner, item.getCreditCode(), item.getCreatedAt(), item.getUpdatedAt()), item.getIntro(), null, null);
            }
            case "SOLUTION" -> {
                Solution item = required(solutions.selectById(bizId), "解决方案");
                SupplierCompany supplier = suppliers.selectById(item.getSupplierId());
                yield new AuditDetail(new AuditQueueItem(item.getId(), type, item.getTitle(), item.getAuditStatus(),
                    supplier == null ? "供应商资料待补充" : supplier.getName(), item.getSceneTags(), item.getCreatedAt(), item.getUpdatedAt()),
                    item.getSummary(), item.getContent(), null);
            }
            default -> throw new IllegalArgumentException("不支持的审核对象类型");
        };
    }

    @GetMapping("/logs")
    public IPage<AuditRecord> logs(@RequestParam(required = false) String bizType,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "20") long size) {
        String type = bizType == null || bizType.isBlank() ? null : auditService.normalizeType(bizType);
        return records.selectPage(Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
            new LambdaQueryWrapper<AuditRecord>()
                .eq(type != null, AuditRecord::getBizType, type)
                .and(keyword != null && !keyword.isBlank(), q -> q.like(AuditRecord::getOperatorName, keyword)
                    .or().like(AuditRecord::getOpinion, keyword))
                .orderByDesc(AuditRecord::getCreatedAt));
    }

    private AuditPage<AuditQueueItem> topicPage(Page<Topic> page, String status, String keyword, LocalDateTime start, LocalDateTime end) {
        IPage<Topic> result = topics.selectPage(page, auditQuery(Topic::getStatus, Topic::getTitle, Topic::getCreatedAt, status, keyword, start, end));
        return page(result, item -> new AuditQueueItem(item.getId(), "TOPIC", item.getTitle(), item.getStatus(),
            item.getSourceDesc(), "专题分类 #" + item.getCategoryId(), item.getCreatedAt(), item.getUpdatedAt()));
    }

    private AuditPage<AuditQueueItem> articlePage(Page<Article> page, String status, String keyword, LocalDateTime start, LocalDateTime end) {
        IPage<Article> result = articles.selectPage(page, auditQuery(Article::getStatus, Article::getTitle, Article::getCreatedAt, status, keyword, start, end));
        return page(result, item -> new AuditQueueItem(item.getId(), "ARTICLE", item.getTitle(), item.getStatus(),
            item.getSourceName(), "资讯分类 #" + item.getCategoryId(), item.getCreatedAt(), item.getUpdatedAt()));
    }

    private AuditPage<AuditQueueItem> supplierPage(Page<SupplierCompany> page, String status, String keyword, LocalDateTime start, LocalDateTime end) {
        IPage<SupplierCompany> result = suppliers.selectPage(page,
            auditQuery(SupplierCompany::getVerificationStatus, SupplierCompany::getName, SupplierCompany::getCreatedAt, status, keyword, start, end));
        Map<Long, String> userNames = userNames(result.getRecords().stream().map(SupplierCompany::getOwnerUserId).toList());
        return page(result, item -> new AuditQueueItem(item.getId(), "SUPPLIER", item.getName(), item.getVerificationStatus(),
            userNames.getOrDefault(item.getOwnerUserId(), "供应商账号"), item.getCreditCode(), item.getCreatedAt(), item.getUpdatedAt()));
    }

    private AuditPage<AuditQueueItem> solutionPage(Page<Solution> page, String status, String keyword, LocalDateTime start, LocalDateTime end) {
        IPage<Solution> result = solutions.selectPage(page,
            auditQuery(Solution::getAuditStatus, Solution::getTitle, Solution::getCreatedAt, status, keyword, start, end));
        List<Long> supplierIds = result.getRecords().stream().map(Solution::getSupplierId).distinct().toList();
        Map<Long, String> supplierNames = supplierIds.isEmpty() ? Map.of() : suppliers.selectBatchIds(supplierIds)
            .stream().collect(Collectors.toMap(SupplierCompany::getId, SupplierCompany::getName));
        return page(result, item -> new AuditQueueItem(item.getId(), "SOLUTION", item.getTitle(), item.getAuditStatus(),
            supplierNames.getOrDefault(item.getSupplierId(), "供应商资料待补充"), item.getSceneTags(), item.getCreatedAt(), item.getUpdatedAt()));
    }

    private <T> LambdaQueryWrapper<T> auditQuery(com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> statusColumn,
                                                   com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> titleColumn,
                                                   com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, LocalDateTime> createdColumn,
                                                   String status, String keyword, LocalDateTime start, LocalDateTime end) {
        return new LambdaQueryWrapper<T>()
            .eq(status != null && !status.isBlank(), statusColumn, status)
            .like(keyword != null && !keyword.isBlank(), titleColumn, keyword)
            .ge(start != null, createdColumn, start)
            .le(end != null, createdColumn, end)
            .orderByDesc(createdColumn);
    }

    private Map<Long, String> userNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.selectBatchIds(ids).stream().collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName));
    }

    private <T> AuditPage<AuditQueueItem> page(IPage<T> result, Function<T, AuditQueueItem> mapper) {
        return new AuditPage<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream().map(mapper).toList());
    }

    private static <T> T required(T value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不存在");
        return value;
    }

    public record AuditRequest(String status, String opinion) { }
    public record AuditPage<T>(long current, long size, long total, List<T> records) { }
    public record AuditQueueItem(Long id, String bizType, String title, String status, String submitter,
                                 String detail, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record AuditDetail(AuditQueueItem item, String summary, String content, String sourceUrl) { }
}
