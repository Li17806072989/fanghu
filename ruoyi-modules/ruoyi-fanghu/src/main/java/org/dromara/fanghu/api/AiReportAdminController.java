package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.fanghu.service.AiReportService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-reports")
@SaCheckPermission("platform:audit")
public class AiReportAdminController {
    private final AiReportService reports;

    public AiReportAdminController(AiReportService reports) { this.reports = reports; }

    @GetMapping
    public List<AiReportService.JobSummary> jobs(@RequestParam(required = false) String accessStatus) {
        return reports.adminJobs(accessStatus);
    }

    @PatchMapping("/{id}/access")
    @Log(title = "AI汇报全文授权", businessType = BusinessType.GRANT)
    public AiReportService.JobSummary decide(@PathVariable Long id, @RequestBody Decision request) {
        if (request == null) throw new IllegalArgumentException("请提交授权结果");
        return reports.decide(id, request.decision(), request.opinion());
    }

    public record Decision(String decision, String opinion) { }
}
