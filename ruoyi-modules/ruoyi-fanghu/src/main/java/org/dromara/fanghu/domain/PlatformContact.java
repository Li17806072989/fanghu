package org.dromara.fanghu.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fh_platform_contact")
public class PlatformContact extends FanghuEntity {
    private String hotline;
    private String email;
    private String address;
    private String serviceHours;
}
