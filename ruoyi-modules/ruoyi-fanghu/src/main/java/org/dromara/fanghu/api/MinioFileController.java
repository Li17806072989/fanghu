package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.fanghu.domain.CustomerProfile;
import org.dromara.fanghu.domain.AdSlot;
import org.dromara.fanghu.domain.FileAsset;
import org.dromara.fanghu.domain.FileDownloadLog;
import org.dromara.fanghu.domain.Lead;
import org.dromara.fanghu.domain.Solution;
import org.dromara.fanghu.domain.SupplierCompany;
import org.dromara.fanghu.domain.Topic;
import org.dromara.fanghu.mapper.CustomerProfileMapper;
import org.dromara.fanghu.mapper.AdSlotMapper;
import org.dromara.fanghu.mapper.ArticleMapper;
import org.dromara.fanghu.mapper.FileAssetMapper;
import org.dromara.fanghu.mapper.FileDownloadLogMapper;
import org.dromara.fanghu.mapper.LeadMapper;
import org.dromara.fanghu.mapper.SolutionMapper;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.fanghu.mapper.TopicMapper;
import org.dromara.fanghu.security.PlatformAccessDeniedException;
import org.dromara.fanghu.service.LeadWorkflowService;
import org.dromara.fanghu.service.VirusScanService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/storage")
public class MinioFileController {
    private final MinioClient client;
    private final String bucket;
    private final int presignExpirySeconds;
    private final long maxFileSizeBytes;
    private final VirusScanService scanner;
    private final FileAssetMapper files;
    private final FileDownloadLogMapper downloadLogs;
    private final SolutionMapper solutions;
    private final SupplierMapper suppliers;
    private final TopicMapper topics;
    private final ArticleMapper articles;
    private final AdSlotMapper ads;
    private final LeadMapper leads;
    private final CustomerProfileMapper profiles;
    private final LeadWorkflowService leadWorkflow;

    public MinioFileController(@Value("${fanghu.minio.endpoint}") String endpoint,
                               @Value("${fanghu.minio.access-key}") String key,
                               @Value("${fanghu.minio.secret-key}") String secret,
                               @Value("${fanghu.minio.bucket}") String bucket,
                               @Value("${fanghu.minio.presign-expiry-seconds:900}") int presignExpirySeconds,
                               @Value("${fanghu.storage.max-file-size-bytes:10485760}") long maxFileSizeBytes,
                               VirusScanService scanner, FileAssetMapper files,
                               FileDownloadLogMapper downloadLogs, SolutionMapper solutions,
                               SupplierMapper suppliers, TopicMapper topics, ArticleMapper articles, AdSlotMapper ads, LeadMapper leads,
                               CustomerProfileMapper profiles, LeadWorkflowService leadWorkflow) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(key, secret).build();
        this.bucket = bucket;
        this.presignExpirySeconds = Math.max(60, Math.min(presignExpirySeconds, 604800));
        this.maxFileSizeBytes = Math.max(1, maxFileSizeBytes);
        this.scanner = scanner;
        this.files = files;
        this.downloadLogs = downloadLogs;
        this.solutions = solutions;
        this.suppliers = suppliers;
        this.topics = topics;
        this.articles = articles;
        this.ads = ads;
        this.leads = leads;
        this.profiles = profiles;
        this.leadWorkflow = leadWorkflow;
    }

    @SaCheckLogin
    @PostMapping("/upload")
    @Log(title = "安全文件上传", businessType = BusinessType.INSERT, excludeParamNames = {"file"})
    public FileAsset upload(@RequestPart MultipartFile file,
                            @RequestParam String ownerType,
                            @RequestParam Long ownerId) {
        if (file.isEmpty()) throw new IllegalArgumentException("不能上传空文件");
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("文件不能超过" + (maxFileSizeBytes / 1024 / 1024) + "MB");
        }
        String normalizedOwnerType = ownerType.trim().toUpperCase(Locale.ROOT);
        ensureUploadOwner(normalizedOwnerType, ownerId);
        validateUploadType(file, normalizedOwnerType);
        Path temporaryFile = null;
        String objectKey = null;
        boolean objectUploaded = false;
        try {
            temporaryFile = Files.createTempFile("fanghu-upload-", ".bin");
            file.transferTo(temporaryFile);
            if ("TOPIC".equals(normalizedOwnerType) || "ARTICLE".equals(normalizedOwnerType)
                || "AD".equals(normalizedOwnerType)) {
                validateContentImage(file, temporaryFile);
            }
            String sha256 = sha256(temporaryFile);
            VirusScanService.ScanResult scan = scanner.scan(temporaryFile);
            if (scan.status() == VirusScanService.ScanStatus.INFECTED) {
                throw new IllegalArgumentException("文件包含恶意内容，已拒绝上传");
            }
            if (scan.status() != VirusScanService.ScanStatus.PASSED) {
                throw new IllegalStateException("文件未通过安全扫描，未保存");
            }

            ensureBucket();
            String safeName = safeFileName(file.getOriginalFilename());
            objectKey = normalizedOwnerType.toLowerCase(Locale.ROOT) + "/" + ownerId + "/"
                + UUID.randomUUID() + "-" + safeName;
            try (InputStream stream = Files.newInputStream(temporaryFile)) {
                client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(stream, Files.size(temporaryFile), -1)
                    .contentType(contentType(file)).build());
            }
            objectUploaded = true;

            FileAsset asset = new FileAsset();
            asset.setOwnerType(normalizedOwnerType);
            asset.setOwnerId(ownerId);
            asset.setObjectKey(objectKey);
            asset.setSha256(sha256);
            asset.setVisibility("AUTHORIZED");
            asset.setScanStatus("PASSED");
            asset.setDownloadCount(0);
            files.insert(asset);
            return asset;
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (objectUploaded) removeQuietly(objectKey);
            throw e;
        } catch (Exception e) {
            if (objectUploaded) removeQuietly(objectKey);
            throw new IllegalStateException("文件上传失败，未保存", e);
        } finally {
            if (temporaryFile != null) {
                try { Files.deleteIfExists(temporaryFile); } catch (Exception ignored) { }
            }
        }
    }

    @SaCheckLogin
    @DeleteMapping("/{id}")
    @Log(title = "安全文件删除", businessType = BusinessType.DELETE)
    public boolean delete(@PathVariable Long id) {
        FileAsset asset = files.selectById(id);
        if (asset == null) throw new IllegalArgumentException("文件不存在");
        if (!isAdmin()) ensureUploadOwner(asset.getOwnerType(), asset.getOwnerId());
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(asset.getObjectKey()).build());
        } catch (Exception e) {
            throw new IllegalStateException("对象存储删除失败，数据库记录未删除", e);
        }
        return files.deleteById(id) > 0;
    }

    @GetMapping("/public/{id}")
    public void publicFile(@PathVariable Long id, HttpServletResponse response) {
        FileAsset asset = files.selectById(id);
        if (asset == null || !("TOPIC".equalsIgnoreCase(asset.getOwnerType())
            || "ARTICLE".equalsIgnoreCase(asset.getOwnerType())
            || "AD".equalsIgnoreCase(asset.getOwnerType())) || !"PASSED".equals(asset.getScanStatus())) {
            throw new IllegalArgumentException("公开图片不存在");
        }
        if ("TOPIC".equalsIgnoreCase(asset.getOwnerType()) && topics.selectById(asset.getOwnerId()) == null) {
            throw new IllegalArgumentException("专题不存在");
        }
        if ("ARTICLE".equalsIgnoreCase(asset.getOwnerType()) && articles.selectById(asset.getOwnerId()) == null) {
            throw new IllegalArgumentException("资讯不存在");
        }
        if ("AD".equalsIgnoreCase(asset.getOwnerType())) {
            AdSlot ad = ads.selectById(asset.getOwnerId());
            if (ad == null || !("/api/storage/public/" + asset.getId()).equals(ad.getImageUrl())) {
                throw new IllegalArgumentException("广告图片不存在");
            }
        }
        response.setContentType(publicImageContentType(asset.getObjectKey()));
        response.setHeader("Cache-Control", "public, max-age=3600");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try (InputStream stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(asset.getObjectKey()).build())) {
            stream.transferTo(response.getOutputStream());
        } catch (Exception e) {
            throw new IllegalStateException("图片读取失败", e);
        }
    }

    @SaCheckLogin
    @GetMapping("/{id}/download")
    public DownloadTicket download(@PathVariable Long id, HttpServletRequest request) {
        FileAsset asset = files.selectById(id);
        if (asset == null) throw new IllegalArgumentException("文件不存在");
        try {
            if (!"PASSED".equals(asset.getScanStatus())) {
                throw new PlatformAccessDeniedException("文件尚未通过安全扫描");
            }
            if (!"AUTHORIZED".equals(asset.getVisibility())) {
                throw new PlatformAccessDeniedException("文件当前不可下载");
            }
            ensureDownloadOwner(asset.getOwnerType(), asset.getOwnerId());
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(asset.getObjectKey())
                .expiry(presignExpirySeconds).build());
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(presignExpirySeconds);
            recordDownload(asset.getId(), request, "ISSUED", expiresAt);
            files.incrementDownloadCount(asset.getId());
            return new DownloadTicket(asset.getId(), url, presignExpirySeconds, expiresAt);
        } catch (PlatformAccessDeniedException e) {
            recordDownload(asset.getId(), request, "DENIED", null);
            throw e;
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("无法生成下载链接，请稍后重试", e);
        }
    }

    @SaCheckLogin
    @GetMapping("/{id}/download-logs")
    public List<FileDownloadLog> downloadLogs(@PathVariable Long id) {
        if (!isAdmin()) throw new PlatformAccessDeniedException("仅运营管理员可查看下载安全日志");
        if (files.selectById(id) == null) throw new IllegalArgumentException("文件不存在");
        return downloadLogs.selectList(new LambdaQueryWrapper<FileDownloadLog>()
            .eq(FileDownloadLog::getFileId, id)
            .orderByDesc(FileDownloadLog::getCreatedAt)
            .last("LIMIT 100"));
    }

    private void ensureUploadOwner(String type, Long id) {
        if ("TOPIC".equals(type)) {
            if (!isAdmin()) throw new PlatformAccessDeniedException("仅内容运营人员可上传专题封面");
            if (topics.selectById(id) == null) throw new IllegalArgumentException("专题不存在");
            return;
        }
        if ("ARTICLE".equals(type)) {
            if (!isAdmin()) throw new PlatformAccessDeniedException("仅内容运营人员可上传资讯图片");
            if (articles.selectById(id) == null) throw new IllegalArgumentException("资讯不存在");
            return;
        }
        if ("AD".equals(type)) {
            if (!isAdmin()) throw new PlatformAccessDeniedException("仅首页运营人员可上传广告图片");
            if (ads.selectById(id) == null) throw new IllegalArgumentException("广告位不存在");
            return;
        }
        if (isAdmin()) return;
        if ("SUPPLIER".equals(type)) {
            ensureSupplierOwner(id);
            return;
        }
        if ("SOLUTION".equals(type)) {
            Solution solution = solutions.selectById(id);
            if (solution != null) {
                ensureSupplierOwner(solution.getSupplierId());
                return;
            }
        }
        throw new PlatformAccessDeniedException("无权上传该文件资源");
    }

    private void ensureDownloadOwner(String type, Long id) {
        if (isAdmin()) return;
        if ("SUPPLIER".equalsIgnoreCase(type)) {
            ensureSupplierOwner(id);
            return;
        }
        if ("SOLUTION".equalsIgnoreCase(type)) {
            Solution solution = solutions.selectById(id);
            if (solution == null) throw new IllegalArgumentException("解决方案不存在");
            if (isSupplierOwner(solution.getSupplierId()) || isCustomerAuthorized(solution)) return;
        }
        throw new PlatformAccessDeniedException("无权访问该文件资源");
    }

    private void ensureSupplierOwner(Long supplierId) {
        if (!isSupplierOwner(supplierId)) {
            throw new PlatformAccessDeniedException("无权访问该供应商资源");
        }
    }

    private boolean isSupplierOwner(Long supplierId) {
        return supplierId != null && suppliers.selectOne(new LambdaQueryWrapper<SupplierCompany>()
            .eq(SupplierCompany::getId, supplierId)
            .eq(SupplierCompany::getOwnerUserId, userId())) != null;
    }

    private boolean isCustomerAuthorized(Solution solution) {
        if (!"APPROVED".equals(solution.getAuditStatus())) return false;
        CustomerProfile profile = profiles.selectOne(new LambdaQueryWrapper<CustomerProfile>()
            .eq(CustomerProfile::getUserId, userId()));
        if (profile == null) return false;
        return leads.selectList(new LambdaQueryWrapper<Lead>()
            .eq(Lead::getCustomerId, profile.getId())
            .eq(Lead::getSupplierId, solution.getSupplierId()))
            .stream().anyMatch(leadWorkflow::contactReleased);
    }

    private void recordDownload(Long fileId, HttpServletRequest request, String result, LocalDateTime expiresAt) {
        FileDownloadLog log = new FileDownloadLog();
        log.setFileId(fileId);
        log.setUserId(userId());
        log.setUserRole(currentRole());
        log.setIpAddress(truncate(request.getRemoteAddr(), 64));
        log.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        log.setResult(result);
        log.setExpiresAt(expiresAt);
        downloadLogs.insert(log);
    }

    private String currentRole() {
        if (LoginHelper.isSuperAdmin() || StpUtil.hasRole("admin")) return "ADMIN";
        if (StpUtil.hasRole("supplier")) return "SUPPLIER";
        if (StpUtil.hasRole("customer")) return "CUSTOMER";
        return "USER";
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String safeFileName(String originalName) {
        String name = originalName == null ? "file" : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "file" : truncate(name, 160);
    }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank()
            ? "application/octet-stream" : file.getContentType();
    }

    private void validateContentImage(MultipartFile file, Path path) throws Exception {
        if (file.getSize() > 8L * 1024 * 1024) throw new IllegalArgumentException("内容图片不能超过8MB");
        String type = contentType(file).toLowerCase(Locale.ROOT);
        if (!("image/jpeg".equals(type) || "image/png".equals(type) || "image/gif".equals(type))) {
            throw new IllegalArgumentException("内容图片仅支持 JPG、PNG 或 GIF 图片");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean extensionMatches = ("image/jpeg".equals(type) && (name.endsWith(".jpg") || name.endsWith(".jpeg")))
            || ("image/png".equals(type) && name.endsWith(".png"))
            || ("image/gif".equals(type) && name.endsWith(".gif"));
        if (!extensionMatches) throw new IllegalArgumentException("内容图片的文件类型与扩展名不一致");
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("上传文件不是有效图片");
        if (image.getWidth() > 8000 || image.getHeight() > 8000) throw new IllegalArgumentException("内容图片尺寸不能超过8000×8000像素");
    }

    private void validateUploadType(MultipartFile file, String ownerType) {
        if ("TOPIC".equals(ownerType) || "ARTICLE".equals(ownerType) || "AD".equals(ownerType)) return;
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        if (!List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "7z").contains(extension)) {
            throw new IllegalArgumentException("附件类型不受支持，仅允许 PDF、Office 文档、文本或压缩包");
        }
        String type = contentType(file).toLowerCase(Locale.ROOT);
        if (type.contains("executable") || type.contains("x-msdownload") || extension.equals("exe") || extension.equals("dll")) {
            throw new IllegalArgumentException("禁止上传可执行文件");
        }
    }

    private String publicImageContentType(String objectKey) {
        String value = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isAdmin() {
        return LoginHelper.isSuperAdmin() || StpUtil.hasRole("admin");
    }

    private Long userId() {
        return org.dromara.common.satoken.utils.LoginHelper.getUserId();
    }

    private synchronized void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void removeQuietly(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ignored) { }
    }

    public record DownloadTicket(Long fileId, String url, int expiresIn, LocalDateTime expiresAt) { }
}
