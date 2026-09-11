package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Data;

@Data
@TableName("fh_topic")
public class Topic extends BaseEntity {
    private Long categoryId;
    private String title;
    private String summary;
    private String coverUrl;
    private String sourceDesc;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String detailContent;
    private String status;
    private Boolean isRecommended;
    private java.time.LocalDateTime publishedAt;
    @TableField(exist = false)
    private List<Long> taxonomyIds;
}
