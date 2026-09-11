package com.fanghu.platform.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fanghu.platform.domain.Article;
import com.fanghu.platform.domain.Solution;
import com.fanghu.platform.domain.Topic;
import com.fanghu.platform.domain.SupplierMembership;
import com.fanghu.platform.mapper.ArticleMapper;
import com.fanghu.platform.mapper.SolutionMapper;
import com.fanghu.platform.mapper.TopicMapper;
import com.fanghu.platform.mapper.SupplierMembershipMapper;
import com.fanghu.platform.service.TaxonomyService;
import com.fanghu.platform.service.WorkflowStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private static final Set<String> CONTENT_TYPES = Set.of("ALL", "TOPIC", "SOLUTION", "ARTICLE");
    private final TopicMapper topics;
    private final SolutionMapper solutions;
    private final ArticleMapper articles;
    private final SupplierMembershipMapper memberships;
    private final TaxonomyService taxonomy;

    public SearchController(TopicMapper topics, SolutionMapper solutions, ArticleMapper articles,
                            SupplierMembershipMapper memberships,
                            TaxonomyService taxonomy) {
        this.topics = topics;
        this.solutions = solutions;
        this.articles = articles;
        this.memberships = memberships;
        this.taxonomy = taxonomy;
    }

    @GetMapping
    public SearchPage search(@RequestParam(defaultValue = "ALL") String contentType,
                             @RequestParam(defaultValue = "1") long page,
                             @RequestParam(defaultValue = "20") long size,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) List<Long> taxonomyIds,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedFrom,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedTo) {
        String type = contentType == null ? "ALL" : contentType.trim().toUpperCase();
        if (!CONTENT_TYPES.contains(type)) throw new IllegalArgumentException("不支持的搜索内容类型");
        String term = normalizeKeyword(keyword);
        List<Long> selectedTaxonomies = taxonomyIds == null ? List.of() : taxonomyIds.stream().distinct().toList();
        if (selectedTaxonomies.size() > 8) throw new IllegalArgumentException("一次最多选择8个分类条件");
        if (publishedFrom != null && publishedTo != null && publishedFrom.isAfter(publishedTo)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        long normalizedPage = Math.max(1, page);
        long normalizedSize = Math.max(1, Math.min(size, 100));
        String effectiveStatus = effectiveStatus(status);
        if ("ALL".equals(type)) {
            return searchAll(normalizedPage, normalizedSize, term, selectedTaxonomies, effectiveStatus, publishedFrom, publishedTo);
        }
        return switch (type) {
            case "TOPIC" -> topicPage(normalizedPage, normalizedSize, term, selectedTaxonomies, effectiveStatus, publishedFrom, publishedTo);
            case "SOLUTION" -> solutionPage(normalizedPage, normalizedSize, term, selectedTaxonomies, effectiveStatus, publishedFrom, publishedTo);
            case "ARTICLE" -> articlePage(normalizedPage, normalizedSize, term, selectedTaxonomies, effectiveStatus, publishedFrom, publishedTo);
            default -> throw new IllegalArgumentException("不支持的搜索内容类型");
        };
    }

    private SearchPage topicPage(long page, long size, String keyword, List<Long> taxonomyIds, String status,
                                 LocalDate from, LocalDate to) {
        Set<Long> ids = taxonomy.matchingContentIds("TOPIC", taxonomyIds);
        if (ids != null && ids.isEmpty()) return SearchPage.empty(page, size);
        LambdaQueryWrapper<Topic> query = new LambdaQueryWrapper<Topic>()
            .and(keyword != null, q -> q.like(Topic::getTitle, keyword).or().like(Topic::getSummary, keyword).or().like(Topic::getSourceDesc, keyword))
            .eq(Topic::getStatus, status)
            .in(ids != null, Topic::getId, ids)
            .ge(from != null, Topic::getPublishedAt, atStart(from))
            .lt(to != null, Topic::getPublishedAt, nextDay(to))
            .orderByDesc(Topic::getPublishedAt).orderByDesc(Topic::getCreatedAt);
        IPage<Topic> result = topics.selectPage(Page.of(page, size), query);
        Map<Long, List<String>> labels = taxonomy.labels("TOPIC", result.getRecords().stream().map(Topic::getId).toList());
        return new SearchPage(page, size, result.getTotal(), result.getRecords().stream().map(item ->
            record(item.getId(), "TOPIC", item.getTitle(), item.getSummary(), item.getStatus(),
                value(item.getSourceDesc(), "来源待补充"), value(item.getPublishedAt(), item.getCreatedAt()),
                labels.get(item.getId()), item.getSourceUrl(), item.getCoverUrl())).toList());
    }

    private SearchPage solutionPage(long page, long size, String keyword, List<Long> taxonomyIds, String status,
                                    LocalDate from, LocalDate to) {
        Set<Long> ids = taxonomy.matchingContentIds("SOLUTION", taxonomyIds);
        if (ids != null && ids.isEmpty()) return SearchPage.empty(page, size);
        List<Long> promotedSupplierIds = promotedSupplierIds();
        LambdaQueryWrapper<Solution> query = new LambdaQueryWrapper<Solution>()
            .and(keyword != null, q -> q.like(Solution::getTitle, keyword).or().like(Solution::getSummary, keyword)
                .or().like(Solution::getContent, keyword).or().like(Solution::getSceneTags, keyword))
            .eq(Solution::getAuditStatus, status)
            .in(ids != null, Solution::getId, ids)
            .ge(from != null, Solution::getUpdatedAt, atStart(from))
            .lt(to != null, Solution::getUpdatedAt, nextDay(to));
        if (promotedSupplierIds.isEmpty()) {
            query.orderByDesc(Solution::getUpdatedAt);
        } else {
            String idsCsv = promotedSupplierIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            query.last("ORDER BY CASE WHEN supplier_id IN (" + idsCsv + ") THEN 0 ELSE 1 END, updated_at DESC");
        }
        IPage<Solution> result = solutions.selectPage(Page.of(page, size), query);
        Map<Long, List<String>> labels = taxonomy.labels("SOLUTION", result.getRecords().stream().map(Solution::getId).toList());
        return new SearchPage(page, size, result.getTotal(), result.getRecords().stream().map(item -> {
            boolean promoted = promotedSupplierIds.contains(item.getSupplierId());
            return
            record(item.getId(), "SOLUTION", item.getTitle(), item.getSummary(), item.getAuditStatus(),
                promoted ? "会员优先展示 · 平台审核后公开" : "平台审核后公开", item.getUpdatedAt(), labels.get(item.getId()), promoted);
        }).toList());
    }

    private SearchPage articlePage(long page, long size, String keyword, List<Long> taxonomyIds, String status,
                                   LocalDate from, LocalDate to) {
        Set<Long> ids = taxonomy.matchingContentIds("ARTICLE", taxonomyIds);
        if (ids != null && ids.isEmpty()) return SearchPage.empty(page, size);
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
            .and(keyword != null, q -> q.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword)
                .or().like(Article::getContent, keyword).or().like(Article::getSourceName, keyword))
            .eq(Article::getStatus, status)
            .in(ids != null, Article::getId, ids)
            .ge(from != null, Article::getPublishedAt, atStart(from))
            .lt(to != null, Article::getPublishedAt, nextDay(to))
            .orderByDesc(Article::getPublishedAt).orderByDesc(Article::getCreatedAt);
        IPage<Article> result = articles.selectPage(Page.of(page, size), query);
        Map<Long, List<String>> labels = taxonomy.labels("ARTICLE", result.getRecords().stream().map(Article::getId).toList());
        return new SearchPage(page, size, result.getTotal(), result.getRecords().stream().map(item ->
            record(item.getId(), "ARTICLE", item.getTitle(), item.getSummary(), item.getStatus(),
                value(item.getSourceName(), "来源待补充"), value(item.getPublishedAt(), item.getCreatedAt()),
                labels.get(item.getId()), item.getSourceUrl(), item.getImageUrl())).toList());
    }

    private SearchPage searchAll(long page, long size, String keyword, List<Long> taxonomyIds, String status,
                                 LocalDate from, LocalDate to) {
        List<SearchRecord> combined = new ArrayList<>();
        combined.addAll(topicPage(1, 500, keyword, taxonomyIds, status, from, to).records());
        combined.addAll(solutionPage(1, 500, keyword, taxonomyIds, status, from, to).records());
        combined.addAll(articlePage(1, 500, keyword, taxonomyIds, status, from, to).records());
        combined.sort(Comparator.comparing(SearchRecord::promoted).reversed()
            .thenComparing(SearchRecord::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int start = (int) Math.min(combined.size(), (page - 1) * size);
        int end = (int) Math.min(combined.size(), start + size);
        return new SearchPage(page, size, combined.size(), combined.subList(start, end));
    }

    private String effectiveStatus(String requested) {
        if (!isAdmin()) return "APPROVED";
        return requested == null || requested.isBlank() ? "APPROVED" : WorkflowStatus.audit(requested.trim().toUpperCase());
    }

    private boolean isAdmin() { return StpUtil.isLogin() && StpUtil.hasRole("admin"); }
    private LocalDateTime atStart(LocalDate value) { return value == null ? null : value.atStartOfDay(); }
    private LocalDateTime nextDay(LocalDate value) { return value == null ? null : value.plusDays(1).atStartOfDay(); }
    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) return null;
        String term = value.trim();
        if (term.length() > 100) throw new IllegalArgumentException("关键词不能超过100字");
        return term;
    }
    private SearchRecord record(Long id, String type, String title, String summary, String status,
                                String source, LocalDateTime publishedAt, List<String> categories) {
        return record(id, type, title, summary, status, source, publishedAt, categories, false, null);
    }

    private SearchRecord record(Long id, String type, String title, String summary, String status,
                                String source, LocalDateTime publishedAt, List<String> categories, boolean promoted) {
        return record(id, type, title, summary, status, source, publishedAt, categories, promoted, null);
    }

    private SearchRecord record(Long id, String type, String title, String summary, String status,
                                String source, LocalDateTime publishedAt, List<String> categories, String sourceUrl) {
        return record(id, type, title, summary, status, source, publishedAt, categories, false, sourceUrl);
    }

    private SearchRecord record(Long id, String type, String title, String summary, String status,
                                String source, LocalDateTime publishedAt, List<String> categories,
                                boolean promoted, String sourceUrl) {
        return record(id, type, title, summary, status, source, publishedAt, categories, promoted, sourceUrl, null);
    }

    private SearchRecord record(Long id, String type, String title, String summary, String status,
                                String source, LocalDateTime publishedAt, List<String> categories,
                                String sourceUrl, String imageUrl) {
        return record(id, type, title, summary, status, source, publishedAt, categories, false, sourceUrl, imageUrl);
    }

    private SearchRecord record(Long id, String type, String title, String summary, String status,
                                String source, LocalDateTime publishedAt, List<String> categories,
                                boolean promoted, String sourceUrl, String imageUrl) {
        return new SearchRecord(id, type, title, summary, status, source, publishedAt,
            categories == null ? List.of() : categories, promoted, sourceUrl, imageUrl);
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
    private <T> T value(T value, T fallback) { return value == null ? fallback : value; }

    public record SearchPage(long page, long size, long total, List<SearchRecord> records) {
        static SearchPage empty(long page, long size) { return new SearchPage(page, size, 0, List.of()); }
    }
    public record SearchRecord(Long id, String contentType, String title, String summary, String status,
                               String source, LocalDateTime publishedAt, List<String> categories,
                               boolean promoted, String sourceUrl, String imageUrl) { }
}
