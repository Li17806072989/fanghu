package org.dromara.fanghu.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_content_taxonomy")
public class ContentTaxonomy extends FanghuEntity {
    private String contentType;
    private Long contentId;
    private Long taxonomyId;
    private Boolean primaryFlag;
}
