package com.fanghu.platform.domain;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime; import lombok.Data;
@Data public abstract class BaseEntity { @TableId(type=IdType.AUTO) private Long id; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt; }
