package com.fanghu.platform.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fanghu.platform.domain.FileAsset;
import com.fanghu.platform.domain.Solution;
import com.fanghu.platform.mapper.AdSlotMapper;
import com.fanghu.platform.mapper.ArticleMapper;
import com.fanghu.platform.mapper.CustomerProfileMapper;
import com.fanghu.platform.mapper.FileAssetMapper;
import com.fanghu.platform.mapper.FileDownloadLogMapper;
import com.fanghu.platform.mapper.LeadMapper;
import com.fanghu.platform.mapper.SolutionMapper;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.mapper.TopicMapper;
import com.fanghu.platform.service.DetailContentService;
import com.fanghu.platform.service.LeadWorkflowService;
import com.fanghu.platform.service.VirusScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MinioFileControllerTest {
    private final FileAssetMapper files = mock(FileAssetMapper.class);
    private final SolutionMapper solutions = mock(SolutionMapper.class);
    private final DetailContentService details = new DetailContentService(new ObjectMapper(), files);
    private MinioFileController controller;

    @BeforeEach
    void setUp() {
        controller = new MinioFileController("http://localhost:9000", "key", "secret", "bucket", 900,
            mock(VirusScanService.class), files, mock(FileDownloadLogMapper.class), solutions,
            mock(SupplierMapper.class), mock(TopicMapper.class), mock(ArticleMapper.class), mock(AdSlotMapper.class),
            mock(LeadMapper.class), mock(CustomerProfileMapper.class), mock(LeadWorkflowService.class), details);
    }

    @Test
    void rejectsDetailImageOwnedByDraftSolution() {
        FileAsset asset = solutionDetailAsset();
        Solution solution = solution("PENDING", "[{\"type\":\"IMAGE\",\"imageUrl\":\"/api/storage/public/42\"}]");
        when(solutions.selectById(7L)).thenReturn(solution);

        assertThrows(IllegalArgumentException.class, () -> controller.ensurePublicAsset(asset));
    }

    @Test
    void rejectsImageNoLongerReferencedByApprovedSolution() {
        FileAsset asset = solutionDetailAsset();
        when(solutions.selectById(7L)).thenReturn(solution("APPROVED", "[{\"type\":\"TEXT\",\"text\":\"公开正文\"}]"));

        assertThrows(IllegalArgumentException.class, () -> controller.ensurePublicAsset(asset));
    }

    @Test
    void allowsReferencedImageFromApprovedSolution() {
        FileAsset asset = solutionDetailAsset();
        when(solutions.selectById(7L)).thenReturn(solution(
            "APPROVED", "[{\"type\":\"IMAGE\",\"imageUrl\":\"/api/storage/public/42\"}]"));

        assertDoesNotThrow(() -> controller.ensurePublicAsset(asset));
    }

    private FileAsset solutionDetailAsset() {
        FileAsset asset = new FileAsset();
        asset.setId(42L);
        asset.setOwnerType("SOLUTION_DETAIL");
        asset.setOwnerId(7L);
        asset.setScanStatus("PASSED");
        return asset;
    }

    private Solution solution(String status, String detailContent) {
        Solution solution = new Solution();
        solution.setId(7L);
        solution.setAuditStatus(status);
        solution.setDetailContent(detailContent);
        return solution;
    }
}
