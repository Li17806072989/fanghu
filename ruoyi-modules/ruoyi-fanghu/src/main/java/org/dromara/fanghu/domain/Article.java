package org.dromara.fanghu.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Data;

@Data
@TableName("fh_article")
public class Article extends FanghuEntity {
    private Long categoryId;
    private String title;
    private String summary;
    private String imageUrl;
    private String content;
    private String sourceName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String detailContent;
    private String status;
    private Boolean isHot;
    private java.time.LocalDateTime publishedAt;
    @TableField(exist = false)
    private List<Long> taxonomyIds;
}
