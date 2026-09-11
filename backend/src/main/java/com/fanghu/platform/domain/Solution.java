package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Data;

@Data
@TableName("fh_solution")
public class Solution extends BaseEntity {
    private Long supplierId;
    private String title;
    private Long categoryId;
    private String sceneTags;
    private String summary;
    private String content;
    private String auditStatus;
    private String visibility;
    @TableField(exist = false)
    private List<Long> taxonomyIds;
    @TableField(exist = false)
    private Boolean promoted;
}
