package org.dromara.fanghu.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.dromara.fanghu.domain.FileAsset;
import org.dromara.fanghu.mapper.FileAssetMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DetailContentService {
    private static final String PUBLIC_IMAGE_PREFIX = "/api/storage/public/";
    private final JsonMapper objectMapper;
    private final FileAssetMapper files;

    public DetailContentService(JsonMapper objectMapper, FileAssetMapper files) {
        this.objectMapper = objectMapper;
        this.files = files;
    }

    public String normalize(String json, String ownerType, Long ownerId) {
        if (json == null || json.isBlank()) return null;
        try {
            List<DetailBlock> input = objectMapper.readValue(json, new TypeReference<>() { });
            if (input == null || input.isEmpty()) return null;
            if (input.size() > 60) throw new IllegalArgumentException("站内详情最多添加60个内容块");
            List<DetailBlock> normalized = new ArrayList<>();
            for (DetailBlock block : input) normalized.add(normalizeBlock(block, ownerType, ownerId));
            return objectMapper.writeValueAsString(normalized);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("站内详情内容格式无效");
        }
    }

    public List<DetailBlock> decode(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<DetailBlock> blocks = objectMapper.readValue(json, new TypeReference<>() { });
            return blocks == null ? List.of() : blocks;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private DetailBlock normalizeBlock(DetailBlock block, String ownerType, Long ownerId) {
        if (block == null || block.type() == null) throw new IllegalArgumentException("请选择详情内容类型");
        String type = block.type().trim().toUpperCase(Locale.ROOT);
        String linkUrl = optional(block.linkUrl(), 500, "跳转链接");
        if (linkUrl != null) validateHttpUrl(linkUrl);
        if ("TEXT".equals(type)) {
            return new DetailBlock(type, required(block.text(), 5000, "文字内容"), null, null, linkUrl);
        }
        if ("IMAGE".equals(type)) {
            String imageUrl = required(block.imageUrl(), 500, "详情图片");
            validateImageOwnership(imageUrl, ownerType, ownerId);
            return new DetailBlock(type, null, imageUrl, optional(block.caption(), 300, "图片说明"), linkUrl);
        }
        throw new IllegalArgumentException("不支持的详情内容类型");
    }

    private void validateImageOwnership(String imageUrl, String ownerType, Long ownerId) {
        if (!imageUrl.startsWith(PUBLIC_IMAGE_PREFIX)) throw new IllegalArgumentException("详情图片地址无效");
        try {
            Long fileId = Long.valueOf(imageUrl.substring(PUBLIC_IMAGE_PREFIX.length()));
            FileAsset asset = files.selectById(fileId);
            if (asset == null || !ownerType.equalsIgnoreCase(asset.getOwnerType())
                || !ownerId.equals(asset.getOwnerId()) || !"PASSED".equals(asset.getScanStatus())) {
                throw new IllegalArgumentException("详情图片不属于当前内容或尚未通过安全扫描");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("详情图片地址无效");
        }
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ignored) {
            throw new IllegalArgumentException("跳转链接必须是有效的 HTTP(S) 地址");
        }
    }

    private String required(String value, int maxLength, String label) {
        String result = optional(value, maxLength, label);
        if (result == null) throw new IllegalArgumentException(label + "不能为空");
        return result;
    }

    private String optional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maxLength) throw new IllegalArgumentException(label + "不能超过" + maxLength + "字");
        return result;
    }

    public record DetailBlock(String type, String text, String imageUrl, String caption, String linkUrl) { }
}
