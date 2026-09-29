package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.fanghu.domain.*;
import org.dromara.fanghu.mapper.*;
import java.util.List;
import org.dromara.fanghu.service.TaxonomyService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final TopicCategoryMapper topics;
    private final ArticleCategoryMapper articles;
    private final TaxonomyService taxonomy;

    public CategoryController(TopicCategoryMapper topics, ArticleCategoryMapper articles, TaxonomyService taxonomy) {
        this.topics = topics;
        this.articles = articles;
        this.taxonomy = taxonomy;
    }

    @GetMapping("/topics")
    public List<TopicCategory> topicCategories() {
        return topics.selectList(new LambdaQueryWrapper<TopicCategory>()
            .eq(TopicCategory::getEnabled, true).orderByAsc(TopicCategory::getSortNo));
    }

    @GetMapping("/articles")
    public List<ArticleCategory> articleCategories() {
        return articles.selectList(new LambdaQueryWrapper<ArticleCategory>()
            .eq(ArticleCategory::getEnabled, true).orderByAsc(ArticleCategory::getSortNo));
    }

    @GetMapping("/taxonomy/tree")
    public List<TaxonomyService.TreeNode> taxonomyTree(@RequestParam String type) {
        return taxonomy.tree(type);
    }

    @GetMapping("/taxonomy")
    public List<TaxonomyNode> taxonomyNodes(@RequestParam String type,
                                            @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return taxonomy.nodes(type, enabledOnly);
    }

    @SaCheckPermission("platform:write")
    @PostMapping("/taxonomy")
    @Log(title = "平台分类", businessType = BusinessType.INSERT)
    public TaxonomyNode createTaxonomy(@RequestBody TaxonomyNode node) {
        return taxonomy.create(node);
    }

    @SaCheckPermission("platform:write")
    @PutMapping("/taxonomy/{id}")
    @Log(title = "平台分类", businessType = BusinessType.UPDATE)
    public TaxonomyNode updateTaxonomy(@PathVariable Long id, @RequestBody TaxonomyNode node) {
        return taxonomy.update(id, node);
    }
}
