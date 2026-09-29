package org.dromara.fanghu.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_topic_category")
public class TopicCategory extends FanghuEntity {
    private Long parentId;
    private String name;
    private Integer sortNo;
    private Boolean enabled;
}
