package org.dromara.fanghu.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.fanghu.domain.AdSlot;
import org.dromara.fanghu.domain.Article;
import org.dromara.fanghu.domain.PlatformContact;
import org.dromara.fanghu.domain.Topic;
import org.dromara.fanghu.mapper.AdSlotMapper;
import org.dromara.fanghu.mapper.ArticleMapper;
import org.dromara.fanghu.mapper.PlatformContactMapper;
import org.dromara.fanghu.mapper.TopicMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/homepage")
public class HomepageController {
    private final AdSlotMapper ads;
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final PlatformContactMapper contacts;

    public HomepageController(AdSlotMapper ads, TopicMapper topics, ArticleMapper articles,
                              PlatformContactMapper contacts) {
        this.ads = ads;
        this.topics = topics;
        this.articles = articles;
        this.contacts = contacts;
    }

    @GetMapping
    public HomepagePayload homepage() {
        LocalDateTime now = LocalDateTime.now();
        List<AdSlot> activeAds = ads.selectList(new LambdaQueryWrapper<AdSlot>()
            .eq(AdSlot::getStatus, "ACTIVE")
            .and(q -> q.isNull(AdSlot::getStartAt).or().le(AdSlot::getStartAt, now))
            .and(q -> q.isNull(AdSlot::getEndAt).or().ge(AdSlot::getEndAt, now))
            .orderByAsc(AdSlot::getSortNo).orderByDesc(AdSlot::getUpdatedAt));
        List<Topic> recommended = topics.selectPage(Page.of(1, 6), new LambdaQueryWrapper<Topic>()
            .eq(Topic::getStatus, "APPROVED").eq(Topic::getIsRecommended, true)
            .orderByDesc(Topic::getPublishedAt).orderByDesc(Topic::getUpdatedAt)).getRecords();
        List<Article> hot = articles.selectPage(Page.of(1, 8), new LambdaQueryWrapper<Article>()
            .eq(Article::getStatus, "APPROVED").eq(Article::getIsHot, true)
            .orderByDesc(Article::getPublishedAt).orderByDesc(Article::getUpdatedAt)).getRecords();
        return new HomepagePayload(activeAds, recommended, hot, contacts.selectById(1L));
    }

    public record HomepagePayload(List<AdSlot> ads, List<Topic> recommendedTopics,
                                  List<Article> hotArticles, PlatformContact contact) { }
}
