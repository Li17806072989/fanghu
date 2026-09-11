package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_topic_category")
public class TopicCategory extends BaseEntity {
    private Long parentId;
    private String name;
    private Integer sortNo;
    private Boolean enabled;
}
