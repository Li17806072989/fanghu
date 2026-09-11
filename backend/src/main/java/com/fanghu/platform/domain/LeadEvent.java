package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_lead_event")
public class LeadEvent extends BaseEntity {
    private Long leadId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String remark;
    private Long actorUserId;
    private String actorName;
    private String actorRole;
}
