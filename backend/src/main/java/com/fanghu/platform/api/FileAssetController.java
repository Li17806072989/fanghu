package com.fanghu.platform.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.CustomerProfile;
import com.fanghu.platform.domain.FileAsset;
import com.fanghu.platform.domain.Lead;
import com.fanghu.platform.domain.Solution;
import com.fanghu.platform.domain.SupplierCompany;
import com.fanghu.platform.mapper.CustomerProfileMapper;
import com.fanghu.platform.mapper.FileAssetMapper;
import com.fanghu.platform.mapper.LeadMapper;
import com.fanghu.platform.mapper.SolutionMapper;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.security.PlatformAccessDeniedException;
import com.fanghu.platform.service.LeadWorkflowService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileAssetController {
    private final FileAssetMapper files;
    private final SupplierMapper suppliers;
    private final SolutionMapper solutions;
    private final LeadMapper leads;
    private final CustomerProfileMapper profiles;
    private final LeadWorkflowService leadWorkflow;

    public FileAssetController(FileAssetMapper files, SupplierMapper suppliers,
                               SolutionMapper solutions, LeadMapper leads,
                               CustomerProfileMapper profiles, LeadWorkflowService leadWorkflow) {
        this.files = files;
        this.suppliers = suppliers;
        this.solutions = solutions;
        this.leads = leads;
        this.profiles = profiles;
        this.leadWorkflow = leadWorkflow;
    }

    @SaCheckLogin
    @GetMapping
    public List<FileAsset> list(@RequestParam String ownerType, @RequestParam Long ownerId) {
        boolean owner = ensureReadAccess(ownerType, ownerId);
        LambdaQueryWrapper<FileAsset> query = new LambdaQueryWrapper<FileAsset>()
            .eq(FileAsset::getOwnerType, ownerType.toUpperCase())
            .eq(FileAsset::getOwnerId, ownerId)
            .orderByDesc(FileAsset::getCreatedAt);
        if (!owner && !isAdmin()) {
            query.eq(FileAsset::getVisibility, "AUTHORIZED").eq(FileAsset::getScanStatus, "PASSED");
        }
        return files.selectList(query);
    }

    @SaCheckLogin
    @PostMapping
    public boolean create(@RequestBody FileAsset file) {
        ensureWriteAccess(file.getOwnerType(), file.getOwnerId());
        file.setOwnerType(file.getOwnerType().toUpperCase());
        file.setScanStatus("PENDING");
        file.setVisibility("PRIVATE");
        file.setDownloadCount(0);
        return files.insert(file) > 0;
    }

    private boolean ensureReadAccess(String type, Long id) {
        if (isAdmin()) {
            return true;
        }
        if ("SUPPLIER".equalsIgnoreCase(type) && isSupplierOwner(id)) {
            return true;
        }
        if ("SOLUTION".equalsIgnoreCase(type)) {
            Solution solution = solutions.selectById(id);
            if (solution != null && isSupplierOwner(solution.getSupplierId())) {
                return true;
            }
            if (solution != null && isCustomerAuthorized(solution)) {
                return false;
            }
        }
        throw new PlatformAccessDeniedException("无权访问该文件资源");
    }

    private void ensureWriteAccess(String type, Long id) {
        if (isAdmin()) {
            return;
        }
        if ("SUPPLIER".equalsIgnoreCase(type) && isSupplierOwner(id)) {
            return;
        }
        if ("SOLUTION".equalsIgnoreCase(type)) {
            Solution solution = solutions.selectById(id);
            if (solution != null && isSupplierOwner(solution.getSupplierId())) {
                return;
            }
        }
        throw new PlatformAccessDeniedException("无权创建该文件资源");
    }

    private boolean isSupplierOwner(Long supplierId) {
        return supplierId != null && suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>()
            .eq(SupplierCompany::getId, supplierId)
            .eq(SupplierCompany::getOwnerUserId, userId())) != null;
    }

    private boolean isCustomerAuthorized(Solution solution) {
        if (!"APPROVED".equals(solution.getAuditStatus())) {
            return false;
        }
        CustomerProfile profile = profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>()
            .eq(CustomerProfile::getUserId, userId()));
        return profile != null && leads.selectList(new LambdaQueryWrapper<Lead>()
            .eq(Lead::getCustomerId, profile.getId())
            .eq(Lead::getSupplierId, solution.getSupplierId()))
            .stream().anyMatch(leadWorkflow::contactReleased);
    }

    private boolean isAdmin() {
        return StpUtil.hasRole("admin");
    }

    private Long userId() {
        return StpUtil.getLoginIdAsLong();
    }
}
