package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.fanghu.domain.Article;
import org.dromara.fanghu.domain.ArticleCategory;
import org.dromara.fanghu.domain.AuditRecord;
import org.dromara.fanghu.domain.FileAsset;
import org.dromara.system.domain.SysUser;
import org.dromara.fanghu.domain.Topic;
import org.dromara.fanghu.mapper.ArticleCategoryMapper;
import org.dromara.fanghu.mapper.ArticleMapper;
import org.dromara.fanghu.mapper.AuditRecordMapper;
import org.dromara.fanghu.mapper.FileAssetMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.fanghu.mapper.TopicMapper;
import org.dromara.fanghu.service.TaxonomyService;
import org.dromara.fanghu.service.StorageCleanupService;
import org.dromara.fanghu.service.DetailContentService;
import org.dromara.fanghu.service.WorkflowStatus;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content")
@SaCheckPermission("platform:write")
public class ContentManagementController {
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final ArticleCategoryMapper articleCategories;
    private final FileAssetMapper files;
    private final AuditRecordMapper auditRecords;
    private final SysUserMapper users;
    private final TaxonomyService taxonomy;
    private final StorageCleanupService storageCleanup;
    private final DetailContentService detailContents;

    public ContentManagementController(TopicMapper topics, ArticleMapper articles, ArticleCategoryMapper articleCategories,
                                       FileAssetMapper files, AuditRecordMapper auditRecords, SysUserMapper users,
                                       TaxonomyService taxonomy, StorageCleanupService storageCleanup,
                                       DetailContentService detailContents) {
        this.topics = topics;
        this.articles = articles;
        this.articleCategories = articleCategories;
        this.files = files;
        this.auditRecords = auditRecords;
        this.users = users;
        this.taxonomy = taxonomy;
        this.storageCleanup = storageCleanup;
        this.detailContents = detailContents;
    }

    @GetMapping("/topics")
    public ContentPage<TopicRow> topics(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        IPage<Topic> result = topics.selectPage(requestPage(page, size), new LambdaQueryWrapper<Topic>()
            .eq(hasText(status), Topic::getStatus, normalizedStatus(status))
            .and(hasText(keyword), query -> query.like(Topic::getTitle, keyword.trim())
                .or().like(Topic::getSourceDesc, keyword.trim()))
            .orderByDesc(Topic::getUpdatedAt));
        return new ContentPage<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream()
            .map(item -> new TopicRow(item.getId(), item.getTitle(), item.getSummary(), item.getSourceDesc(), item.getSourceUrl(),
                item.getStatus(), item.getIsRecommended(), item.getPublishedAt(), item.getUpdatedAt())).toList());
    }

    @GetMapping("/topics/{id}")
    public Topic topic(@PathVariable Long id) {
        Topic topic = required(topics.selectById(id), "专题不存在");
        topic.setTaxonomyIds(taxonomy.assignments("TOPIC", List.of(id)).getOrDefault(id, List.of()));
        return topic;
    }

    @PostMapping("/topics")
    @Log(title = "专题管理", businessType = BusinessType.INSERT)
    @Transactional
    public Topic createTopic(@RequestBody TopicForm form) {
        validateTopic(form, false);
        if (hasText(form.coverUrl())) throw new IllegalArgumentException("请先保存专题草稿再上传封面");
        Topic topic = apply(new Topic(), form);
        topic.setStatus("DRAFT");
        topic.setIsRecommended(false);
        topics.insert(topic);
        topic.setDetailContent(detailContents.normalize(form.detailContent(), "TOPIC", topic.getId()));
        topics.updateById(topic);
        taxonomy.sync("TOPIC", topic.getId(), topic.getCategoryId(), form.taxonomyIds());
        topic.setTaxonomyIds(taxonomy.assignments("TOPIC", List.of(topic.getId())).getOrDefault(topic.getId(), List.of()));
        return topic;
    }

    @PutMapping("/topics/{id}")
    @Log(title = "专题管理", businessType = BusinessType.UPDATE)
    @Transactional
    public Topic updateTopic(@PathVariable Long id, @RequestBody TopicForm form) {
        validateTopic(form, false);
        Topic topic = apply(required(topics.selectById(id), "专题不存在"), form);
        validateCoverOwnership(topic.getCoverUrl(), id);
        topic.setDetailContent(detailContents.normalize(form.detailContent(), "TOPIC", id));
        topic.setStatus("DRAFT");
        topic.setIsRecommended(false);
        topic.setPublishedAt(null);
        topics.updateById(topic);
        taxonomy.sync("TOPIC", id, topic.getCategoryId(), form.taxonomyIds());
        topic.setTaxonomyIds(taxonomy.assignments("TOPIC", List.of(id)).getOrDefault(id, List.of()));
        return topic;
    }

    @PatchMapping("/topics/{id}/submit")
    @Log(title = "专题提交审核", businessType = BusinessType.UPDATE)
    public Topic submitTopic(@PathVariable Long id) {
        Topic topic = required(topics.selectById(id), "专题不存在");
        validateTopic(new TopicForm(topic.getTitle(), topic.getSummary(), topic.getCoverUrl(), topic.getSourceDesc(), topic.getSourceUrl(),
            topic.getCategoryId(), taxonomy.assignments("TOPIC", List.of(id)).getOrDefault(id, List.of()),
            topic.getDetailContent()), true);
        ensureSubmittable(topic.getStatus());
        topic.setStatus("PENDING");
        topic.setIsRecommended(false);
        topics.updateById(topic);
        return topic;
    }

    @DeleteMapping("/topics/{id}")
    @Log(title = "专题管理", businessType = BusinessType.DELETE)
    @Transactional
    public void deleteTopic(@PathVariable Long id) {
        Topic topic = required(topics.selectById(id), "专题不存在");
        storageCleanup.deleteOwnerFiles("TOPIC", id);
        taxonomy.sync("TOPIC", id, null, List.of());
        topics.deleteById(id);
        recordDeletion("TOPIC", id, topic.getStatus());
    }

    @GetMapping("/articles")
    public ContentPage<ArticleRow> articles(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        IPage<Article> result = articles.selectPage(requestPage(page, size), new LambdaQueryWrapper<Article>()
            .eq(hasText(status), Article::getStatus, normalizedStatus(status))
            .and(hasText(keyword), query -> query.like(Article::getTitle, keyword.trim())
                .or().like(Article::getSourceName, keyword.trim()))
            .orderByDesc(Article::getUpdatedAt));
        return new ContentPage<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords().stream()
            .map(item -> new ArticleRow(item.getId(), item.getTitle(), item.getSummary(), item.getImageUrl(), item.getSourceName(),
                item.getSourceUrl(), item.getStatus(), item.getIsHot(), item.getPublishedAt(), item.getUpdatedAt())).toList());
    }

    @GetMapping("/articles/{id}")
    public Article article(@PathVariable Long id) {
        Article article = required(articles.selectById(id), "资讯不存在");
        article.setTaxonomyIds(taxonomy.assignments("ARTICLE", List.of(id)).getOrDefault(id, List.of()));
        return article;
    }

    @PostMapping("/articles")
    @Log(title = "资讯管理", businessType = BusinessType.INSERT)
    @Transactional
    public Article createArticle(@RequestBody ArticleForm form) {
        validateArticle(form, false);
        if (hasText(form.imageUrl())) throw new IllegalArgumentException("请先保存资讯草稿再上传图片");
        Article article = apply(new Article(), form);
        article.setStatus("DRAFT");
        article.setIsHot(false);
        articles.insert(article);
        article.setDetailContent(detailContents.normalize(form.detailContent(), "ARTICLE", article.getId()));
        articles.updateById(article);
        taxonomy.sync("ARTICLE", article.getId(), null, form.taxonomyIds());
        article.setTaxonomyIds(taxonomy.assignments("ARTICLE", List.of(article.getId())).getOrDefault(article.getId(), List.of()));
        return article;
    }

    @PutMapping("/articles/{id}")
    @Log(title = "资讯管理", businessType = BusinessType.UPDATE)
    @Transactional
    public Article updateArticle(@PathVariable Long id, @RequestBody ArticleForm form) {
        validateArticle(form, false);
        Article article = apply(required(articles.selectById(id), "资讯不存在"), form);
        validateImageOwnership(article.getImageUrl(), "ARTICLE", id, "资讯图片");
        article.setDetailContent(detailContents.normalize(form.detailContent(), "ARTICLE", id));
        article.setStatus("DRAFT");
        article.setIsHot(false);
        articles.updateById(article);
        taxonomy.sync("ARTICLE", id, null, form.taxonomyIds());
        article.setTaxonomyIds(taxonomy.assignments("ARTICLE", List.of(id)).getOrDefault(id, List.of()));
        return article;
    }

    @PatchMapping("/articles/{id}/submit")
    @Log(title = "资讯提交审核", businessType = BusinessType.UPDATE)
    public Article submitArticle(@PathVariable Long id) {
        Article article = required(articles.selectById(id), "资讯不存在");
        validateArticle(new ArticleForm(article.getCategoryId(), article.getTitle(), article.getSummary(), article.getImageUrl(), article.getContent(),
            article.getSourceName(), article.getSourceUrl(), article.getPublishedAt(),
            taxonomy.assignments("ARTICLE", List.of(id)).getOrDefault(id, List.of()), article.getDetailContent()), true);
        ensureSubmittable(article.getStatus());
        article.setStatus("PENDING");
        article.setIsHot(false);
        articles.updateById(article);
        return article;
    }

    @DeleteMapping("/articles/{id}")
    @Log(title = "资讯管理", businessType = BusinessType.DELETE)
    @Transactional
    public void deleteArticle(@PathVariable Long id) {
        Article article = required(articles.selectById(id), "资讯不存在");
        storageCleanup.deleteOwnerFiles("ARTICLE", id);
        taxonomy.sync("ARTICLE", id, null, List.of());
        articles.deleteById(id);
        recordDeletion("ARTICLE", id, article.getStatus());
    }

    private Topic apply(Topic topic, TopicForm form) {
        topic.setTitle(trim(form.title(), 200, "专题标题", true));
        topic.setSummary(trim(form.summary(), 500, "专题摘要", false));
        topic.setCoverUrl(trim(form.coverUrl(), 500, "专题封面地址", false));
        topic.setSourceDesc(trim(form.sourceDesc(), 255, "来源说明", false));
        topic.setSourceUrl(trim(form.sourceUrl(), 500, "来源链接", false));
        topic.setCategoryId(form.categoryId());
        return topic;
    }

    private Article apply(Article article, ArticleForm form) {
        article.setCategoryId(form.categoryId());
        article.setTitle(trim(form.title(), 200, "资讯标题", true));
        article.setSummary(trim(form.summary(), 500, "资讯摘要", false));
        article.setImageUrl(trim(form.imageUrl(), 500, "资讯图片地址", false));
        article.setContent(trim(form.content(), 100_000, "资讯正文", false));
        article.setSourceName(trim(form.sourceName(), 120, "来源名称", false));
        article.setSourceUrl(trim(form.sourceUrl(), 500, "来源链接", false));
        article.setPublishedAt(form.publishedAt());
        return article;
    }

    private void validateTopic(TopicForm form, boolean submission) {
        if (form == null) throw new IllegalArgumentException("请填写专题内容");
        trim(form.title(), 200, "专题标题", true);
        trim(form.summary(), 500, "专题摘要", false);
        String coverUrl = trim(form.coverUrl(), 500, "专题封面地址", false);
        String source = trim(form.sourceDesc(), 255, "来源说明", false);
        String sourceUrl = trim(form.sourceUrl(), 500, "来源链接", false);
        if (form.categoryId() == null) throw new IllegalArgumentException("请选择装备分类");
        validateTaxonomies(form.taxonomyIds());
        if (coverUrl != null && !coverUrl.startsWith("/api/storage/public/")) throw new IllegalArgumentException("专题封面地址无效");
        if (sourceUrl != null) validateHttpUrl(sourceUrl);
        if (submission && source == null) throw new IllegalArgumentException("提交审核前请填写来源说明");
    }

    private void validateArticle(ArticleForm form, boolean submission) {
        if (form == null) throw new IllegalArgumentException("请填写资讯内容");
        trim(form.title(), 200, "资讯标题", true);
        trim(form.summary(), 500, "资讯摘要", false);
        String imageUrl = trim(form.imageUrl(), 500, "资讯图片地址", false);
        String content = trim(form.content(), 100_000, "资讯正文", false);
        String sourceName = trim(form.sourceName(), 120, "来源名称", false);
        String sourceUrl = trim(form.sourceUrl(), 500, "来源链接", false);
        if (form.categoryId() == null) throw new IllegalArgumentException("请选择资讯分类");
        ArticleCategory category = articleCategories.selectById(form.categoryId());
        if (category == null || !Boolean.TRUE.equals(category.getEnabled())) throw new IllegalArgumentException("资讯分类不存在或已停用");
        validateTaxonomies(form.taxonomyIds());
        if (imageUrl != null && !imageUrl.startsWith("/api/storage/public/")) throw new IllegalArgumentException("资讯图片地址无效");
        if (sourceUrl != null) validateHttpUrl(sourceUrl);
        if (submission && (imageUrl == null || content == null || sourceName == null || form.publishedAt() == null)) {
            throw new IllegalArgumentException("提交审核前请上传资讯图片，并填写正文、来源名称和发布时间");
        }
    }

    private void validateTaxonomies(List<Long> ids) {
        if (ids != null && ids.stream().distinct().count() > 30) throw new IllegalArgumentException("单条内容最多选择30个分类和标签");
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (Exception ignored) {
            throw new IllegalArgumentException("来源链接必须是有效的 HTTP(S) 地址");
        }
    }

    private void validateCoverOwnership(String coverUrl, Long topicId) {
        validateImageOwnership(coverUrl, "TOPIC", topicId, "专题封面");
    }

    private void validateImageOwnership(String imageUrl, String ownerType, Long ownerId, String label) {
        if (imageUrl == null) return;
        try {
            Long fileId = Long.valueOf(imageUrl.substring("/api/storage/public/".length()));
            FileAsset asset = files.selectById(fileId);
            if (asset == null || !ownerType.equals(asset.getOwnerType()) || !ownerId.equals(asset.getOwnerId())
                || !"PASSED".equals(asset.getScanStatus())) throw new IllegalArgumentException();
        } catch (Exception ignored) {
            throw new IllegalArgumentException(label + "不存在或不属于当前内容");
        }
    }

    private void ensureSubmittable(String status) {
        if ("PENDING".equals(status)) throw new IllegalArgumentException("内容已在审核队列中");
        if ("APPROVED".equals(status)) throw new IllegalArgumentException("已发布内容请先编辑并保存为草稿");
    }

    private void recordDeletion(String type, Long id, String beforeStatus) {
        Long operatorId = org.dromara.common.satoken.utils.LoginHelper.getUserId();
        SysUser user = users.selectById(operatorId);
        AuditRecord record = new AuditRecord();
        record.setBizType(type);
        record.setBizId(id);
        record.setAction("DELETED");
        record.setBeforeStatus(beforeStatus);
        record.setAfterStatus("DELETED");
        record.setOpinion("运营人员删除内容");
        record.setOperatorUserId(operatorId);
        record.setOperatorName(user == null ? "未知操作人" : user.getUserName());
        auditRecords.insert(record);
    }

    private String normalizedStatus(String status) {
        return hasText(status) ? WorkflowStatus.audit(status.trim().toUpperCase(Locale.ROOT)) : null;
    }

    private <T> Page<T> requestPage(long page, long size) {
        return Page.of(Math.max(1, page), Math.min(100, Math.max(1, size)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value, int max, String label, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalArgumentException(label + "不能为空");
            return null;
        }
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(label + "不能超过" + max + "字");
        return result;
    }

    private static <T> T required(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    public record TopicForm(String title, String summary, String coverUrl, String sourceDesc, String sourceUrl,
                            Long categoryId, List<Long> taxonomyIds, String detailContent) { }
    public record ArticleForm(Long categoryId, String title, String summary, String imageUrl, String content,
                              String sourceName, String sourceUrl, LocalDateTime publishedAt,
                              List<Long> taxonomyIds, String detailContent) { }
    public record TopicRow(Long id, String title, String summary, String sourceDesc, String sourceUrl, String status,
                           Boolean isRecommended, LocalDateTime publishedAt, LocalDateTime updatedAt) { }
    public record ArticleRow(Long id, String title, String summary, String imageUrl, String sourceName, String sourceUrl,
                             String status, Boolean hot, LocalDateTime publishedAt, LocalDateTime updatedAt) { }
    public record ContentPage<T>(long current, long size, long total, List<T> records) { }
}
