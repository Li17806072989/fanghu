package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_customer_profile")
public class CustomerProfile extends BaseEntity {
    private Long userId;
    private String organization;
    private String contactName;
    private String contactPhone;
    private String verificationStatus;
}
