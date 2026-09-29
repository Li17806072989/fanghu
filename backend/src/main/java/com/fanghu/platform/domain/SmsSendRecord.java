package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fh_sms_send_record")
public class SmsSendRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private String phone;
    private String purpose;
    private String provider;
    private String requestId;
    private String codeHash;
    private String status;
    private String failureReason;
    private String ipAddress;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
