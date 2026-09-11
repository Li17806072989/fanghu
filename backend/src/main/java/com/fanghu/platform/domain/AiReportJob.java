package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fh_ai_job")
public class AiReportJob extends BaseEntity {
    private Long requesterId;
    private String title;
    private String sourceIds;
    private String status;
    private String tocJson;
    private Long resultFileId;
    private String accessStatus;
    private String accessReason;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private String generator;
    private LocalDateTime expiresAt;
}
