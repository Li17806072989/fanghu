package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_audit_record")
public class AuditRecord extends BaseEntity {
    private String bizType;
    private Long bizId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String opinion;
    private Long operatorUserId;
    private String operatorName;
}
