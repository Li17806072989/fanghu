package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fh_file_download_log")
public class FileDownloadLog extends BaseEntity {
    private Long fileId;
    private Long userId;
    private String userRole;
    private String ipAddress;
    private String userAgent;
    private String result;
    private LocalDateTime expiresAt;
}
