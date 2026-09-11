package com.fanghu.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fanghu.platform.domain.FileAsset;
import com.fanghu.platform.mapper.FileAssetMapper;
import org.junit.jupiter.api.Test;

class DetailContentServiceTest {
    private final FileAssetMapper files = mock(FileAssetMapper.class);
    private final DetailContentService service = new DetailContentService(new ObjectMapper(), files);

    @Test
    void normalizesLinkedTextBlock() {
        String json = "[{\"type\":\"text\",\"text\":\"  查看资料  \",\"linkUrl\":\"https://example.com/report\"}]";

        String normalized = service.normalize(json, "TOPIC", 1L);
        DetailContentService.DetailBlock block = service.decode(normalized).get(0);

        assertEquals("TEXT", block.type());
        assertEquals("查看资料", block.text());
        assertEquals("https://example.com/report", block.linkUrl());
    }

    @Test
    void acceptsImageOwnedByCurrentContent() {
        FileAsset asset = new FileAsset();
        asset.setId(9L);
        asset.setOwnerType("ARTICLE");
        asset.setOwnerId(3L);
        asset.setScanStatus("PASSED");
        when(files.selectById(9L)).thenReturn(asset);

        String normalized = service.normalize(
            "[{\"type\":\"IMAGE\",\"imageUrl\":\"/api/storage/public/9\",\"caption\":\"结构示意\"}]",
            "ARTICLE", 3L);

        assertEquals("/api/storage/public/9", service.decode(normalized).get(0).imageUrl());
    }

    @Test
    void rejectsUnsafeLinkScheme() {
        String json = "[{\"type\":\"TEXT\",\"text\":\"危险链接\",\"linkUrl\":\"javascript:alert(1)\"}]";

        assertThrows(IllegalArgumentException.class, () -> service.normalize(json, "TOPIC", 1L));
    }
}
