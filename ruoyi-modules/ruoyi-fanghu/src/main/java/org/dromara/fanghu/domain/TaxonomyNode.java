package org.dromara.fanghu.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_taxonomy_node")
public class TaxonomyNode extends FanghuEntity {
    private Long parentId;
    private String taxonomyType;
    private String code;
    private String name;
    private String description;
    private Integer sortNo;
    private Boolean enabled;
}
