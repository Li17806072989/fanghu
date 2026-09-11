package com.fanghu.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fanghu.platform.domain.AiReportJob;
import com.fanghu.platform.mapper.AiReportJobMapper;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AiReportRecovery {
    private final AiReportJobMapper jobs;
    private final AiReportProcessor processor;

    public AiReportRecovery(AiReportJobMapper jobs, AiReportProcessor processor) {
        this.jobs = jobs;
        this.processor = processor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedJobs() {
        List<AiReportJob> interrupted = jobs.selectPage(Page.of(1, 100), new LambdaQueryWrapper<AiReportJob>()
            .in(AiReportJob::getStatus, List.of("QUEUED", "RUNNING"))
            .orderByAsc(AiReportJob::getCreatedAt)).getRecords();
        interrupted.forEach(job -> processor.process(job.getId()));
    }
}
