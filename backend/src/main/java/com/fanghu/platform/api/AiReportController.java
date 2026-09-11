package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fanghu.platform.service.AiReportService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/reports")
@SaCheckLogin
public class AiReportController {
    private final AiReportService reports;

    public AiReportController(AiReportService reports) { this.reports = reports; }

    @GetMapping("/sources")
    public AiReportService.SourceCatalog sources() { return reports.sources(); }

    @PostMapping
    public AiReportService.JobSummary create(@RequestBody AiReportService.CreateRequest request) {
        return reports.create(request);
    }

    @GetMapping
    public List<AiReportService.JobSummary> mine() { return reports.mine(); }

    @GetMapping("/{id}")
    public AiReportService.JobSummary detail(@PathVariable Long id) { return reports.detail(id); }

    @PostMapping("/{id}/access-request")
    public AiReportService.JobSummary requestAccess(@PathVariable Long id, @RequestBody(required = false) AccessRequest request) {
        return reports.requestAccess(id, request == null ? null : request.reason());
    }

    @GetMapping("/{id}/content")
    public AiReportService.FullReport content(@PathVariable Long id) { return reports.fullContent(id); }

    public record AccessRequest(String reason) { }
}
