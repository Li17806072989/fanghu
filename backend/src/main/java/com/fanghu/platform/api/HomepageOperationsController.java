package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fanghu.platform.domain.AdSlot;
import com.fanghu.platform.domain.Article;
import com.fanghu.platform.domain.FileAsset;
import com.fanghu.platform.domain.PlatformContact;
import com.fanghu.platform.domain.Topic;
import com.fanghu.platform.mapper.AdSlotMapper;
import com.fanghu.platform.mapper.ArticleMapper;
import com.fanghu.platform.mapper.FileAssetMapper;
import com.fanghu.platform.mapper.PlatformContactMapper;
import com.fanghu.platform.mapper.TopicMapper;
import com.fanghu.platform.service.StorageCleanupService;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/homepage")
@SaCheckPermission("platform:write")
public class HomepageOperationsController {
    private static final Set<String> AD_STATUSES = Set.of("DRAFT", "ACTIVE", "OFFLINE");
    private final AdSlotMapper ads;
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final PlatformContactMapper contacts;
    private final FileAssetMapper files;
    private final StorageCleanupService storageCleanup;

    public HomepageOperationsController(AdSlotMapper ads, TopicMapper topics, ArticleMapper articles,
                                        PlatformContactMapper contacts, FileAssetMapper files,
                                        StorageCleanupService storageCleanup) {
        this.ads = ads;
        this.topics = topics;
        this.articles = articles;
        this.contacts = contacts;
        this.files = files;
        this.storageCleanup = storageCleanup;
    }

    @GetMapping
    public OperationsPayload overview() {
        List<AdSlot> allAds = ads.selectList(new LambdaQueryWrapper<AdSlot>()
            .orderByAsc(AdSlot::getSortNo).orderByDesc(AdSlot::getUpdatedAt));
        List<Topic> approvedTopics = topics.selectPage(Page.of(1, 100), new LambdaQueryWrapper<Topic>()
            .eq(Topic::getStatus, "APPROVED").orderByDesc(Topic::getUpdatedAt)).getRecords();
        List<Article> approvedArticles = articles.selectPage(Page.of(1, 100), new LambdaQueryWrapper<Article>()
            .eq(Article::getStatus, "APPROVED").orderByDesc(Article::getPublishedAt)).getRecords();
        return new OperationsPayload(allAds, approvedTopics, approvedArticles, contacts.selectById(1L));
    }

    @PostMapping("/ads")
    public AdSlot createAd(@RequestBody AdSlot ad) {
        validateAd(ad);
        if (ad.getImageUrl() != null) throw new IllegalArgumentException("请先保存广告草稿再上传图片");
        ad.setId(null);
        ads.insert(ad);
        return ad;
    }

    @PutMapping("/ads/{id}")
    @Transactional
    public AdSlot updateAd(@PathVariable Long id, @RequestBody AdSlot form) {
        AdSlot ad = required(ads.selectById(id), "广告位不存在");
        validateAd(form);
        Long retainedFileId = validateAdImageOwnership(form.getImageUrl(), id);
        ad.setSlotCode(form.getSlotCode().trim());
        ad.setTitle(form.getTitle().trim());
        ad.setImageUrl(normalizeUrl(form.getImageUrl()));
        ad.setTargetUrl(normalizeUrl(form.getTargetUrl()));
        ad.setStartAt(form.getStartAt());
        ad.setEndAt(form.getEndAt());
        ad.setStatus(form.getStatus().trim().toUpperCase());
        ad.setSortNo(form.getSortNo() == null ? 0 : form.getSortNo());
        ads.updateById(ad);
        if (retainedFileId != null) storageCleanup.deleteOwnerFilesExcept("AD", id, retainedFileId);
        return ad;
    }

    @DeleteMapping("/ads/{id}")
    @Transactional
    public void deleteAd(@PathVariable Long id) {
        required(ads.selectById(id), "广告位不存在");
        storageCleanup.deleteOwnerFiles("AD", id);
        ads.deleteById(id);
    }

    @PutMapping("/topics/{id}/recommended")
    public void recommendTopic(@PathVariable Long id, @RequestParam boolean value) {
        Topic topic = required(topics.selectById(id), "专题不存在");
        if (value && !"APPROVED".equals(topic.getStatus())) throw new IllegalArgumentException("仅已发布专题可推荐");
        topic.setIsRecommended(value);
        topics.updateById(topic);
    }

    @PutMapping("/articles/{id}/hot")
    public void hotArticle(@PathVariable Long id, @RequestParam boolean value) {
        Article article = required(articles.selectById(id), "资讯不存在");
        if (value && !"APPROVED".equals(article.getStatus())) throw new IllegalArgumentException("仅已发布资讯可设为热点");
        article.setIsHot(value);
        articles.updateById(article);
    }

    @PutMapping("/contact")
    @Transactional
    public PlatformContact updateContact(@RequestBody PlatformContact form) {
        PlatformContact contact = contacts.selectById(1L);
        if (contact == null) {
            contact = new PlatformContact();
            contact.setId(1L);
        }
        contact.setHotline(trim(form.getHotline(), 40, "联系电话"));
        contact.setEmail(trim(form.getEmail(), 120, "联系邮箱"));
        contact.setAddress(trim(form.getAddress(), 255, "联系地址"));
        contact.setServiceHours(trim(form.getServiceHours(), 120, "服务时间"));
        if (contacts.selectById(1L) == null) contacts.insert(contact); else contacts.updateById(contact);
        return contact;
    }

    private void validateAd(AdSlot ad) {
        if (ad == null || ad.getSlotCode() == null || ad.getSlotCode().isBlank()) throw new IllegalArgumentException("广告位编码不能为空");
        if (ad.getTitle() == null || ad.getTitle().isBlank()) throw new IllegalArgumentException("广告标题不能为空");
        String status = ad.getStatus() == null ? "DRAFT" : ad.getStatus().trim().toUpperCase();
        if (!AD_STATUSES.contains(status)) throw new IllegalArgumentException("广告状态不正确");
        if (ad.getStartAt() != null && ad.getEndAt() != null && ad.getStartAt().isAfter(ad.getEndAt())) {
            throw new IllegalArgumentException("广告开始时间不能晚于结束时间");
        }
        ad.setSlotCode(ad.getSlotCode().trim());
        ad.setTitle(ad.getTitle().trim());
        ad.setStatus(status);
        ad.setSortNo(ad.getSortNo() == null ? 0 : ad.getSortNo());
        ad.setImageUrl(normalizeUrl(ad.getImageUrl()));
        ad.setTargetUrl(normalizeUrl(ad.getTargetUrl()));
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        if (!(url.startsWith("/") || url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("链接必须是站内路径或 HTTP(S) 地址");
        }
        return url;
    }

    private Long validateAdImageOwnership(String imageUrl, Long adId) {
        if (imageUrl == null || !imageUrl.startsWith("/api/storage/public/")) return null;
        try {
            Long fileId = Long.valueOf(imageUrl.substring("/api/storage/public/".length()));
            FileAsset asset = files.selectById(fileId);
            if (asset == null || !"AD".equals(asset.getOwnerType()) || !adId.equals(asset.getOwnerId())
                || !"PASSED".equals(asset.getScanStatus())) throw new IllegalArgumentException();
            return fileId;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("广告图片不存在或不属于当前广告");
        }
    }

    private String trim(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(label + "内容过长");
        return result;
    }

    private static <T> T required(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    public record OperationsPayload(List<AdSlot> ads, List<Topic> topics,
                                    List<Article> articles, PlatformContact contact) { }
}
