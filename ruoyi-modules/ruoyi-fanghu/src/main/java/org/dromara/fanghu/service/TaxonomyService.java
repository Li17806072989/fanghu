package org.dromara.fanghu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.fanghu.domain.ContentTaxonomy;
import org.dromara.fanghu.domain.TaxonomyNode;
import org.dromara.fanghu.mapper.ContentTaxonomyMapper;
import org.dromara.fanghu.mapper.TaxonomyNodeMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxonomyService {
    private static final Set<String> TAXONOMY_TYPES = Set.of("TOPIC", "EQUIPMENT", "SCENE", "TECH");
    private static final Set<String> CONTENT_TYPES = Set.of("TOPIC", "SOLUTION", "ARTICLE");
    private final TaxonomyNodeMapper nodes;
    private final ContentTaxonomyMapper links;

    public TaxonomyService(TaxonomyNodeMapper nodes, ContentTaxonomyMapper links) {
        this.nodes = nodes;
        this.links = links;
    }

    public List<TaxonomyNode> nodes(String rawType, boolean enabledOnly) {
        String type = taxonomyType(rawType);
        return nodes.selectList(new LambdaQueryWrapper<TaxonomyNode>()
            .eq(TaxonomyNode::getTaxonomyType, type)
            .eq(enabledOnly, TaxonomyNode::getEnabled, true)
            .orderByAsc(TaxonomyNode::getSortNo).orderByAsc(TaxonomyNode::getId));
    }

    public List<TreeNode> tree(String rawType) {
        List<TaxonomyNode> all = nodes(rawType, true);
        Map<Long, List<TaxonomyNode>> children = new HashMap<>();
        for (TaxonomyNode node : all) children.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
        return buildChildren(0L, children, new HashSet<>());
    }

    @Transactional
    public TaxonomyNode create(TaxonomyNode node) {
        node.setTaxonomyType(taxonomyType(node.getTaxonomyType()));
        validateNode(node, null);
        node.setParentId(node.getParentId() == null ? 0L : node.getParentId());
        node.setCode(node.getCode().trim().toLowerCase());
        node.setName(node.getName().trim());
        node.setSortNo(node.getSortNo() == null ? 0 : node.getSortNo());
        node.setEnabled(node.getEnabled() == null || node.getEnabled());
        nodes.insert(node);
        return node;
    }

    @Transactional
    public TaxonomyNode update(Long id, TaxonomyNode form) {
        TaxonomyNode node = nodes.selectById(id);
        if (node == null) throw new IllegalArgumentException("分类节点不存在");
        form.setTaxonomyType(form.getTaxonomyType() == null ? node.getTaxonomyType() : taxonomyType(form.getTaxonomyType()));
        validateNode(form, id);
        Long parentId = form.getParentId() == null ? 0L : form.getParentId();
        if (id.equals(parentId) || descendants(id).contains(parentId)) throw new IllegalArgumentException("分类节点不能移动到自身或下级节点");
        node.setParentId(parentId);
        node.setTaxonomyType(form.getTaxonomyType());
        node.setCode(form.getCode().trim().toLowerCase());
        node.setName(form.getName().trim());
        node.setDescription(blankToNull(form.getDescription()));
        node.setSortNo(form.getSortNo() == null ? node.getSortNo() : form.getSortNo());
        node.setEnabled(form.getEnabled() == null ? node.getEnabled() : form.getEnabled());
        nodes.updateById(node);
        return node;
    }

    @Transactional
    public void sync(String rawContentType, Long contentId, Long primaryTaxonomyId, Collection<Long> taxonomyIds) {
        String contentType = contentType(rawContentType);
        LinkedHashSet<Long> requested = new LinkedHashSet<>();
        if (primaryTaxonomyId != null) requested.add(primaryTaxonomyId);
        if (taxonomyIds != null) requested.addAll(taxonomyIds.stream().filter(java.util.Objects::nonNull).toList());
        if (!requested.isEmpty()) {
            List<TaxonomyNode> valid = nodes.selectList(new LambdaQueryWrapper<TaxonomyNode>()
                .in(TaxonomyNode::getId, requested).eq(TaxonomyNode::getEnabled, true));
            if (valid.size() != requested.size()) throw new IllegalArgumentException("包含不存在或已停用的分类");
            if (primaryTaxonomyId != null && valid.stream().noneMatch(node -> primaryTaxonomyId.equals(node.getId()) && "EQUIPMENT".equals(node.getTaxonomyType()))) {
                throw new IllegalArgumentException("装备主分类无效");
            }
        }
        links.delete(new LambdaQueryWrapper<ContentTaxonomy>()
            .eq(ContentTaxonomy::getContentType, contentType).eq(ContentTaxonomy::getContentId, contentId));
        for (Long taxonomyId : requested) {
            ContentTaxonomy link = new ContentTaxonomy();
            link.setContentType(contentType);
            link.setContentId(contentId);
            link.setTaxonomyId(taxonomyId);
            link.setPrimaryFlag(taxonomyId.equals(primaryTaxonomyId));
            links.insert(link);
        }
    }

    public Set<Long> matchingContentIds(String rawContentType, Collection<Long> selectedIds) {
        String contentType = contentType(rawContentType);
        if (selectedIds == null || selectedIds.isEmpty()) return null;
        List<Set<Long>> groups = new ArrayList<>();
        Set<Long> union = new HashSet<>();
        for (Long selectedId : new LinkedHashSet<>(selectedIds)) {
            TaxonomyNode selected = nodes.selectById(selectedId);
            if (selected == null || !Boolean.TRUE.equals(selected.getEnabled())) return Set.of();
            Set<Long> group = descendants(selectedId);
            group.add(selectedId);
            groups.add(group);
            union.addAll(group);
        }
        List<ContentTaxonomy> candidates = links.selectList(new LambdaQueryWrapper<ContentTaxonomy>()
            .eq(ContentTaxonomy::getContentType, contentType).in(ContentTaxonomy::getTaxonomyId, union));
        Map<Long, Set<Long>> assigned = new HashMap<>();
        for (ContentTaxonomy link : candidates) assigned.computeIfAbsent(link.getContentId(), key -> new HashSet<>()).add(link.getTaxonomyId());
        Set<Long> matched = new HashSet<>();
        assigned.forEach((id, values) -> {
            if (groups.stream().allMatch(group -> group.stream().anyMatch(values::contains))) matched.add(id);
        });
        return matched;
    }

    public Map<Long, List<String>> labels(String rawContentType, Collection<Long> contentIds) {
        String contentType = contentType(rawContentType);
        if (contentIds == null || contentIds.isEmpty()) return Map.of();
        List<ContentTaxonomy> contentLinks = links.selectList(new LambdaQueryWrapper<ContentTaxonomy>()
            .eq(ContentTaxonomy::getContentType, contentType).in(ContentTaxonomy::getContentId, contentIds));
        if (contentLinks.isEmpty()) return Map.of();
        Map<Long, TaxonomyNode> nodeMap = new HashMap<>();
        for (TaxonomyNode node : nodes.selectBatchIds(contentLinks.stream().map(ContentTaxonomy::getTaxonomyId).collect(java.util.stream.Collectors.toSet()))) {
            nodeMap.put(node.getId(), node);
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (ContentTaxonomy link : contentLinks) {
            TaxonomyNode node = nodeMap.get(link.getTaxonomyId());
            if (node != null) result.computeIfAbsent(link.getContentId(), key -> new ArrayList<>()).add(node.getName());
        }
        result.values().forEach(values -> values.sort(String::compareTo));
        return result;
    }

    public Map<Long, List<Long>> assignments(String rawContentType, Collection<Long> contentIds) {
        String contentType = contentType(rawContentType);
        if (contentIds == null || contentIds.isEmpty()) return Map.of();
        Map<Long, List<Long>> result = new HashMap<>();
        for (ContentTaxonomy link : links.selectList(new LambdaQueryWrapper<ContentTaxonomy>()
            .eq(ContentTaxonomy::getContentType, contentType).in(ContentTaxonomy::getContentId, contentIds))) {
            result.computeIfAbsent(link.getContentId(), key -> new ArrayList<>()).add(link.getTaxonomyId());
        }
        return result;
    }

    private Set<Long> descendants(Long rootId) {
        List<TaxonomyNode> all = nodes.selectList(new LambdaQueryWrapper<TaxonomyNode>().eq(TaxonomyNode::getEnabled, true));
        Map<Long, List<Long>> byParent = new HashMap<>();
        for (TaxonomyNode node : all) byParent.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node.getId());
        Set<Long> result = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(byParent.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            if (result.add(id)) queue.addAll(byParent.getOrDefault(id, List.of()));
        }
        return result;
    }

    private List<TreeNode> buildChildren(Long parentId, Map<Long, List<TaxonomyNode>> children, Set<Long> path) {
        List<TreeNode> result = new ArrayList<>();
        List<TaxonomyNode> current = children.getOrDefault(parentId, List.of()).stream()
            .sorted(Comparator.comparing(TaxonomyNode::getSortNo).thenComparing(TaxonomyNode::getId)).toList();
        for (TaxonomyNode node : current) {
            if (!path.add(node.getId())) continue;
            result.add(new TreeNode(node.getId(), node.getParentId(), node.getTaxonomyType(), node.getCode(),
                node.getName(), node.getDescription(), node.getSortNo(), buildChildren(node.getId(), children, path)));
            path.remove(node.getId());
        }
        return result;
    }

    private void validateNode(TaxonomyNode node, Long currentId) {
        if (node.getCode() == null || !node.getCode().matches("[a-zA-Z0-9-]{2,60}")) throw new IllegalArgumentException("分类编码需为2至60位字母、数字或连字符");
        if (node.getName() == null || node.getName().isBlank() || node.getName().trim().length() > 80) throw new IllegalArgumentException("分类名称不能为空且不能超过80字");
        Long parentId = node.getParentId() == null ? 0L : node.getParentId();
        if (parentId != 0) {
            TaxonomyNode parent = nodes.selectById(parentId);
            if (parent == null || !node.getTaxonomyType().equals(parent.getTaxonomyType())) throw new IllegalArgumentException("上级分类不存在或分类类型不一致");
        }
    }

    public static String taxonomyType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!TAXONOMY_TYPES.contains(normalized)) throw new IllegalArgumentException("不支持的分类类型");
        return normalized;
    }

    public static String contentType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!CONTENT_TYPES.contains(normalized)) throw new IllegalArgumentException("不支持的内容类型");
        return normalized;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record TreeNode(Long id, Long parentId, String taxonomyType, String code, String name,
                           String description, Integer sortNo, List<TreeNode> children) { }
}
