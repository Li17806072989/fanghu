package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fanghu.platform.domain.*;
import com.fanghu.platform.mapper.*;
import com.fanghu.platform.service.AuditService;
import com.fanghu.platform.service.DetailContentService;
import com.fanghu.platform.service.LeadWorkflowService;
import com.fanghu.platform.service.TaxonomyService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api")
public class PlatformApiController {
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final SupplierMapper suppliers;
    private final DemandMapper demands;
    private final LeadMapper leads;
    private final CustomerProfileMapper profiles;
    private final AuditService auditService;
    private final LeadWorkflowService leadWorkflow;
    private final TaxonomyService taxonomy;
    private final DetailContentService detailContents;

    public PlatformApiController(TopicMapper topics, ArticleMapper articles, SupplierMapper suppliers,
                                 DemandMapper demands, LeadMapper leads, CustomerProfileMapper profiles, AuditService auditService,
                                 LeadWorkflowService leadWorkflow, TaxonomyService taxonomy,
                                 DetailContentService detailContents) {
        this.topics = topics;
        this.articles = articles;
        this.suppliers = suppliers;
        this.demands = demands;
        this.leads = leads;
        this.profiles = profiles;
        this.auditService = auditService;
        this.leadWorkflow = leadWorkflow;
        this.taxonomy = taxonomy;
        this.detailContents = detailContents;
    }

    @GetMapping("/topics")
    public List<Topic> topicList() {
        var query = new LambdaQueryWrapper<Topic>().orderByDesc(Topic::getCreatedAt);
        if (!isAdmin()) query.eq(Topic::getStatus, "APPROVED");
        return topics.selectList(query);
    }

    @GetMapping("/topics/page")
    public IPage<Topic> topicPage(@RequestParam(defaultValue = "1") long page,
                                  @RequestParam(defaultValue = "20") long size,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Long categoryId) {
        var query = new LambdaQueryWrapper<Topic>()
            .like(keyword != null && !keyword.isBlank(), Topic::getTitle, keyword)
            .eq(categoryId != null, Topic::getCategoryId, categoryId)
            .orderByDesc(Topic::getCreatedAt);
        if (!isAdmin()) query.eq(Topic::getStatus, "APPROVED");
        return topics.selectPage(Page.of(page, Math.min(size, 100)), query);
    }

    @GetMapping("/topics/{id}")
    public TopicDetail topicDetail(@PathVariable Long id) {
        Topic topic = topics.selectById(id);
        if (topic == null || (!isAdmin() && !"APPROVED".equals(topic.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "专题不存在或尚未发布");
        }
        List<String> categories = taxonomy.labels("TOPIC", List.of(id)).getOrDefault(id, List.of());
        return new TopicDetail(topic.getId(), topic.getTitle(), topic.getSummary(), topic.getCoverUrl(),
            topic.getSourceDesc(), topic.getSourceUrl(), topic.getStatus(), topic.getPublishedAt(), topic.getCreatedAt(),
            topic.getUpdatedAt(), categories, detailContents.decode(topic.getDetailContent()));
    }

    @SaCheckPermission("platform:write")
    @PostMapping("/topics")
    @Transactional
    public Topic createTopic(@RequestBody Topic value) {
        if (value.getTitle() == null || value.getTitle().isBlank() || value.getCategoryId() == null) {
            throw new IllegalArgumentException("专题标题和装备分类不能为空");
        }
        value.setStatus("PENDING");
        topics.insert(value);
        value.setDetailContent(detailContents.normalize(value.getDetailContent(), "TOPIC", value.getId()));
        topics.updateById(value);
        taxonomy.sync("TOPIC", value.getId(), value.getCategoryId(), value.getTaxonomyIds());
        return value;
    }

    @SaCheckPermission("platform:audit")
    @PatchMapping("/topics/{id}/audit")
    public boolean auditTopic(@PathVariable Long id, @RequestParam String status) {
        auditService.audit("TOPIC", id, status, null);
        return true;
    }

    @GetMapping("/articles")
    public List<Article> articleList() {
        var query = new LambdaQueryWrapper<Article>().orderByDesc(Article::getPublishedAt);
        if (!isAdmin()) query.eq(Article::getStatus, "APPROVED");
        return articles.selectList(query);
    }

    @GetMapping("/articles/page")
    public IPage<Article> articlePage(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) String keyword) {
        var query = new LambdaQueryWrapper<Article>()
            .like(keyword != null && !keyword.isBlank(), Article::getTitle, keyword)
            .orderByDesc(Article::getPublishedAt);
        if (!isAdmin()) query.eq(Article::getStatus, "APPROVED");
        return articles.selectPage(Page.of(page, Math.min(size, 100)), query);
    }

    @GetMapping("/articles/{id}")
    public ArticleDetail articleDetail(@PathVariable Long id) {
        Article article = articles.selectById(id);
        if (article == null || (!isAdmin() && !"APPROVED".equals(article.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资讯不存在或尚未发布");
        }
        List<String> categories = taxonomy.labels("ARTICLE", List.of(id)).getOrDefault(id, List.of());
        return new ArticleDetail(article.getId(), article.getTitle(), article.getSummary(), article.getImageUrl(),
            article.getContent(), article.getSourceName(), article.getSourceUrl(), article.getStatus(),
            article.getPublishedAt(), article.getCreatedAt(), article.getUpdatedAt(), categories,
            detailContents.decode(article.getDetailContent()));
    }

    @SaCheckPermission("platform:write")
    @PostMapping("/articles")
    @Transactional
    public Article createArticle(@RequestBody Article value) {
        if (value.getTitle() == null || value.getTitle().isBlank()) throw new IllegalArgumentException("资讯标题不能为空");
        value.setStatus("PENDING");
        articles.insert(value);
        value.setDetailContent(detailContents.normalize(value.getDetailContent(), "ARTICLE", value.getId()));
        articles.updateById(value);
        taxonomy.sync("ARTICLE", value.getId(), null, value.getTaxonomyIds());
        return value;
    }

    @SaCheckPermission("platform:audit")
    @PatchMapping("/articles/{id}/audit")
    public boolean auditArticle(@PathVariable Long id, @RequestParam String status) {
        auditService.audit("ARTICLE", id, status, null);
        return true;
    }

    public record TopicDetail(Long id, String title, String summary, String coverUrl, String sourceDesc, String sourceUrl,
                              String status, java.time.LocalDateTime publishedAt,
                              java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt,
                              List<String> categories, List<DetailContentService.DetailBlock> detailBlocks) { }

    public record ArticleDetail(Long id, String title, String summary, String imageUrl, String content,
                                String sourceName, String sourceUrl, String status,
                                java.time.LocalDateTime publishedAt, java.time.LocalDateTime createdAt,
                                java.time.LocalDateTime updatedAt, List<String> categories,
                                List<DetailContentService.DetailBlock> detailBlocks) { }

    @SaCheckLogin
    @GetMapping("/suppliers")
    public List<SupplierCompany> supplierList() {
        if (isAdmin()) return suppliers.selectList(new LambdaQueryWrapper<>());
        return suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>()
            .eq(SupplierCompany::getOwnerUserId, userId()).orderByDesc(SupplierCompany::getCreatedAt));
    }

    @SaCheckRole(value = {"supplier", "admin"}, mode = SaMode.OR)
    @PostMapping("/suppliers/apply")
    public SupplierCompany applySupplier(@RequestBody SupplierCompany value) {
        Long owner = userId();
        SupplierCompany existing = suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>()
            .eq(SupplierCompany::getOwnerUserId, owner));
        if (existing != null) throw new IllegalArgumentException("当前账号已有供应商档案");
        value.setOwnerUserId(owner);
        value.setVerificationStatus("PENDING");
        value.setServiceStatus("ACTIVE");
        suppliers.insert(value);
        return value;
    }

    @SaCheckPermission("platform:audit")
    @PatchMapping("/suppliers/{id}/verify")
    public boolean verifySupplier(@PathVariable Long id, @RequestParam String status) {
        auditService.audit("SUPPLIER", id, status, null);
        return true;
    }

    @SaCheckRole(value = {"customer", "admin"}, mode = SaMode.OR)
    @GetMapping("/demands")
    public List<Demand> demandList() {
        if (isAdmin()) return demands.selectList(new LambdaQueryWrapper<Demand>().orderByDesc(Demand::getCreatedAt));
        CustomerProfile profile = profile();
        if (profile == null) return List.of();
        return demands.selectList(new LambdaQueryWrapper<Demand>().eq(Demand::getCustomerId, profile.getId()).orderByDesc(Demand::getCreatedAt));
    }

    @SaCheckRole(value = {"customer", "admin"}, mode = SaMode.OR)
    @PostMapping("/demands")
    public Demand createDemand(@RequestBody Demand value) {
        CustomerProfile profile = profile();
        if (profile == null) throw new IllegalArgumentException("请先完善客户联系人档案");
        value.setCustomerId(profile.getId());
        value.setStatus("NEW");
        demands.insert(value);
        leadWorkflow.autoMatch(value.getId());
        return value;
    }

    @SaCheckLogin
    @GetMapping("/leads")
    public List<Lead> leadList() {
        if (isAdmin()) return leads.selectList(new LambdaQueryWrapper<Lead>().orderByDesc(Lead::getCreatedAt));
        List<Long> supplierIds = suppliers.selectList(new LambdaQueryWrapper<SupplierCompany>().eq(SupplierCompany::getOwnerUserId, userId()))
            .stream().map(SupplierCompany::getId).toList();
        CustomerProfile profile = profile();
        LambdaQueryWrapper<Lead> query = new LambdaQueryWrapper<Lead>().orderByDesc(Lead::getCreatedAt);
        if (profile == null && supplierIds.isEmpty()) return List.of();
        if (profile != null && !supplierIds.isEmpty()) query.and(q -> q.eq(Lead::getCustomerId, profile.getId()).or().in(Lead::getSupplierId, supplierIds));
        else if (profile != null) query.eq(Lead::getCustomerId, profile.getId());
        else query.in(Lead::getSupplierId, supplierIds);
        return leads.selectList(query);
    }

    @SaCheckPermission("platform:write")
    @PatchMapping("/leads/{id}/status")
    public boolean updateLead(@PathVariable Long id, @RequestParam String value,
                              @RequestParam(required = false) String remark) {
        leadWorkflow.operatorFollow(id, value, remark, null);
        return true;
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("记录不存在");
        return value;
    }

    private Long userId() { return StpUtil.getLoginIdAsLong(); }
    private boolean isAdmin() { return StpUtil.hasRole("admin"); }
    private void requireAdmin() {
        if (!isAdmin()) throw new IllegalArgumentException("仅平台管理员可执行该操作");
    }
    private CustomerProfile profile() {
        return profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>().eq(CustomerProfile::getUserId, userId()));
    }
}
