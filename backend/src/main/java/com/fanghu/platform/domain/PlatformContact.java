package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_platform_contact")
public class PlatformContact extends BaseEntity {
    private String hotline;
    private String email;
    private String address;
    private String serviceHours;
}
