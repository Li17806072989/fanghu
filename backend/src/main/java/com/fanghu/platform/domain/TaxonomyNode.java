package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_taxonomy_node")
public class TaxonomyNode extends BaseEntity {
    private Long parentId;
    private String taxonomyType;
    private String code;
    private String name;
    private String description;
    private Integer sortNo;
    private Boolean enabled;
}
