package com.fanghu.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fanghu.platform.domain.FileAsset;
import com.fanghu.platform.mapper.FileAssetMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageCleanupService {
    private final MinioClient client;
    private final String bucket;
    private final FileAssetMapper files;

    public StorageCleanupService(@Value("${fanghu.minio.endpoint}") String endpoint,
                                 @Value("${fanghu.minio.access-key}") String key,
                                 @Value("${fanghu.minio.secret-key}") String secret,
                                 @Value("${fanghu.minio.bucket}") String bucket,
                                 FileAssetMapper files) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(key, secret).build();
        this.bucket = bucket;
        this.files = files;
    }

    public void deleteOwnerFiles(String ownerType, Long ownerId) {
        deleteOwnerFilesExcept(ownerType, ownerId, null);
    }

    public void deleteOwnerFilesExcept(String ownerType, Long ownerId, Long retainedFileId) {
        List<FileAsset> assets = files.selectList(new LambdaQueryWrapper<FileAsset>()
            .eq(FileAsset::getOwnerType, ownerType)
            .eq(FileAsset::getOwnerId, ownerId));
        for (FileAsset asset : assets) {
            if (asset.getId().equals(retainedFileId)) continue;
            try {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(asset.getObjectKey()).build());
                files.deleteById(asset.getId());
            } catch (Exception e) {
                throw new IllegalStateException("关联文件清理失败，内容未删除", e);
            }
        }
    }
}
