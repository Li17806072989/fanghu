package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fh_ad_slot")
public class AdSlot extends BaseEntity {
    private String slotCode;
    private String title;
    private String imageUrl;
    private String targetUrl;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private Integer sortNo;
}
