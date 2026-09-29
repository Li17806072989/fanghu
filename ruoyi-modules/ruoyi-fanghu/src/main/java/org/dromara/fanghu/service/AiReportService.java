package org.dromara.fanghu.service;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.dromara.fanghu.domain.AiReportContent;
import org.dromara.fanghu.domain.AiReportJob;
import org.dromara.fanghu.domain.Article;
import org.dromara.fanghu.domain.Solution;
import org.dromara.fanghu.domain.Topic;
import org.dromara.fanghu.mapper.AiReportContentMapper;
import org.dromara.fanghu.mapper.AiReportJobMapper;
import org.dromara.fanghu.mapper.ArticleMapper;
import org.dromara.fanghu.mapper.SolutionMapper;
import org.dromara.fanghu.mapper.TopicMapper;
import org.dromara.fanghu.security.PlatformAccessDeniedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AiReportService {
    private static final Set<String> ACCESS_DECISIONS = Set.of("GRANTED", "DENIED");
    private final AiReportJobMapper jobs;
    private final AiReportContentMapper contents;
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final SolutionMapper solutions;
    private final AiReportProcessor processor;
    private final JsonMapper json;

    public AiReportService(AiReportJobMapper jobs, AiReportContentMapper contents, TopicMapper topics,
                           ArticleMapper articles, SolutionMapper solutions, AiReportProcessor processor,
                           JsonMapper json) {
        this.jobs = jobs;
        this.contents = contents;
        this.topics = topics;
        this.articles = articles;
        this.solutions = solutions;
        this.processor = processor;
        this.json = json;
    }

    public SourceCatalog sources() {
        List<SourceOption> topicOptions = topics.selectPage(Page.of(1, 100), new LambdaQueryWrapper<Topic>()
            .eq(Topic::getStatus, "APPROVED").orderByDesc(Topic::getPublishedAt)).getRecords().stream()
            .map(item -> new SourceOption(item.getId(), item.getTitle(), item.getSummary(), item.getPublishedAt())).toList();
        List<SourceOption> articleOptions = articles.selectPage(Page.of(1, 100), new LambdaQueryWrapper<Article>()
            .eq(Article::getStatus, "APPROVED").orderByDesc(Article::getPublishedAt)).getRecords().stream()
            .map(item -> new SourceOption(item.getId(), item.getTitle(), item.getSummary(), item.getPublishedAt())).toList();
        List<SourceOption> solutionOptions = solutions.selectPage(Page.of(1, 100), new LambdaQueryWrapper<Solution>()
            .eq(Solution::getAuditStatus, "APPROVED").orderByDesc(Solution::getUpdatedAt)).getRecords().stream()
            .map(item -> new SourceOption(item.getId(), item.getTitle(), item.getSummary(), item.getUpdatedAt())).toList();
        return new SourceCatalog(topicOptions, articleOptions, solutionOptions);
    }

    public JobSummary create(CreateRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) throw new IllegalArgumentException("汇报标题不能为空");
        String title = request.title().trim();
        if (title.length() > 200) throw new IllegalArgumentException("汇报标题不能超过200字");
        SourceSelection selection = normalize(request);
        validatePublishedSources(selection);
        List<String> toc = toc(selection);
        AiReportJob job = new AiReportJob();
        job.setRequesterId(org.dromara.common.satoken.utils.LoginHelper.getUserId());
        job.setTitle(title);
        job.setSourceIds(write(selection));
        job.setStatus("QUEUED");
        job.setTocJson(write(toc));
        job.setAccessStatus("NOT_REQUESTED");
        job.setExpiresAt(LocalDateTime.now().plusDays(30));
        jobs.insert(job);
        processor.process(job.getId());
        return summary(job);
    }

    public List<JobSummary> mine() {
        return jobs.selectList(new LambdaQueryWrapper<AiReportJob>()
            .eq(AiReportJob::getRequesterId, org.dromara.common.satoken.utils.LoginHelper.getUserId())
            .orderByDesc(AiReportJob::getCreatedAt)).stream().map(this::summary).toList();
    }

    public JobSummary detail(Long id) { return summary(ownedOrAdmin(id)); }

    public List<JobSummary> adminJobs(String accessStatus) {
        return jobs.selectPage(Page.of(1, 200), new LambdaQueryWrapper<AiReportJob>()
            .eq(accessStatus != null && !accessStatus.isBlank(), AiReportJob::getAccessStatus, accessStatus)
            .orderByDesc(AiReportJob::getCreatedAt)).getRecords().stream().map(this::summary).toList();
    }

    public JobSummary requestAccess(Long id, String reason) {
        AiReportJob job = owned(id);
        requireCompleted(job);
        requireNotExpired(job);
        if ("GRANTED".equals(job.getAccessStatus())) return summary(job);
        job.setAccessStatus("PENDING");
        job.setAccessReason(trim(reason, 500));
        job.setDecidedBy(null);
        job.setDecidedAt(null);
        jobs.updateById(job);
        return summary(job);
    }

    public JobSummary decide(Long id, String decision, String opinion) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if (!ACCESS_DECISIONS.contains(value)) throw new IllegalArgumentException("授权结果必须为通过或拒绝");
        AiReportJob job = required(jobs.selectById(id));
        requireCompleted(job);
        job.setAccessStatus(value);
        job.setAccessReason(trim(opinion, 500));
        job.setDecidedBy(org.dromara.common.satoken.utils.LoginHelper.getUserId());
        job.setDecidedAt(LocalDateTime.now());
        jobs.updateById(job);
        return summary(job);
    }

    public FullReport fullContent(Long id) {
        AiReportJob job = ownedOrAdmin(id);
        requireCompleted(job);
        requireNotExpired(job);
        if (!isAdmin() && !"GRANTED".equals(job.getAccessStatus())) {
            throw new PlatformAccessDeniedException("全文尚未获得运营授权");
        }
        AiReportContent content = contents.selectById(id);
        if (content == null) throw new IllegalArgumentException("汇报正文尚未生成");
        return new FullReport(job.getId(), job.getTitle(), content.getGenerator(), content.getContent(), job.getExpiresAt());
    }

    private SourceSelection normalize(CreateRequest request) {
        List<Long> topicIds = ids(request.topicIds());
        List<Long> articleIds = ids(request.articleIds());
        List<Long> solutionIds = ids(request.solutionIds());
        int count = topicIds.size() + articleIds.size() + solutionIds.size();
        if (count == 0) throw new IllegalArgumentException("请至少选择一条专题、资讯或解决方案");
        if (count > 30) throw new IllegalArgumentException("单个汇报任务最多选择30条资料");
        return new SourceSelection(topicIds, articleIds, solutionIds);
    }

    private void validatePublishedSources(SourceSelection selection) {
        long topicCount = selection.topicIds().isEmpty() ? 0 : topics.selectCount(new LambdaQueryWrapper<Topic>()
            .in(Topic::getId, selection.topicIds()).eq(Topic::getStatus, "APPROVED"));
        long articleCount = selection.articleIds().isEmpty() ? 0 : articles.selectCount(new LambdaQueryWrapper<Article>()
            .in(Article::getId, selection.articleIds()).eq(Article::getStatus, "APPROVED"));
        long solutionCount = selection.solutionIds().isEmpty() ? 0 : solutions.selectCount(new LambdaQueryWrapper<Solution>()
            .in(Solution::getId, selection.solutionIds()).eq(Solution::getAuditStatus, "APPROVED"));
        if (topicCount != selection.topicIds().size() || articleCount != selection.articleIds().size()
            || solutionCount != selection.solutionIds().size()) {
            throw new IllegalArgumentException("所选资料包含不存在或尚未发布的内容，请刷新后重选");
        }
    }

    private List<String> toc(SourceSelection selection) {
        List<String> result = new ArrayList<>();
        result.add("一、汇报目标与资料范围");
        if (!selection.topicIds().isEmpty()) result.add("二、专题推荐与体系脉络（" + selection.topicIds().size() + "项）");
        if (!selection.articleIds().isEmpty()) result.add("三、资讯动态与来源摘要（" + selection.articleIds().size() + "项）");
        if (!selection.solutionIds().isEmpty()) result.add("四、供应商解决方案对照（" + selection.solutionIds().size() + "项）");
        result.add("五、综合要点与待核验事项");
        return result;
    }

    private AiReportJob owned(Long id) {
        AiReportJob job = required(jobs.selectById(id));
        if (!job.getRequesterId().equals(org.dromara.common.satoken.utils.LoginHelper.getUserId())) throw new PlatformAccessDeniedException("不能访问其他用户的汇报任务");
        return job;
    }

    private AiReportJob ownedOrAdmin(Long id) { return isAdmin() ? required(jobs.selectById(id)) : owned(id); }
    private boolean isAdmin() { return LoginHelper.isSuperAdmin() || StpUtil.hasRole("admin"); }
    private void requireCompleted(AiReportJob job) {
        if (!"COMPLETED".equals(job.getStatus())) throw new IllegalArgumentException("汇报任务尚未生成完成");
    }
    private void requireNotExpired(AiReportJob job) {
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(LocalDateTime.now())) throw new PlatformAccessDeniedException("汇报已过期");
    }
    private AiReportJob required(AiReportJob job) {
        if (job == null) throw new IllegalArgumentException("汇报任务不存在");
        return job;
    }
    private List<Long> ids(List<Long> values) { return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values.stream().filter(java.util.Objects::nonNull).toList())); }
    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("说明不能超过" + max + "字");
        return result;
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("汇报数据序列化失败", e); }
    }
    private JobSummary summary(AiReportJob job) {
        try {
            List<String> toc = job.getTocJson() == null ? List.of() : json.readValue(job.getTocJson(), new TypeReference<>() { });
            SourceSelection selection = json.readValue(job.getSourceIds(), SourceSelection.class);
            return new JobSummary(job.getId(), job.getTitle(), job.getStatus(), toc, job.getAccessStatus(), job.getAccessReason(),
                job.getGenerator(), selection.topicIds().size(), selection.articleIds().size(), selection.solutionIds().size(),
                job.getRequesterId(), job.getCreatedAt(), job.getUpdatedAt(), job.getExpiresAt());
        } catch (Exception e) { throw new IllegalStateException("汇报任务数据解析失败", e); }
    }

    public record CreateRequest(String title, List<Long> topicIds, List<Long> articleIds, List<Long> solutionIds) { }
    public record SourceSelection(List<Long> topicIds, List<Long> articleIds, List<Long> solutionIds) { }
    public record SourceOption(Long id, String title, String summary, LocalDateTime publishedAt) { }
    public record SourceCatalog(List<SourceOption> topics, List<SourceOption> articles, List<SourceOption> solutions) { }
    public record JobSummary(Long id, String title, String status, List<String> toc, String accessStatus,
                             String accessReason, String generator, int topicCount, int articleCount, int solutionCount,
                             Long requesterId, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) { }
    public record FullReport(Long id, String title, String generator, String content, LocalDateTime expiresAt) { }
}
