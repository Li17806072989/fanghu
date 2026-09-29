package org.dromara.fanghu.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("fh_article_category")
public class ArticleCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer sortNo;
    private Boolean enabled;
}
