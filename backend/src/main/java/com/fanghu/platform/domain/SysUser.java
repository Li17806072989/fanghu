package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;
    private String userName;
    private String nickName;
    private String userType;
    private String password;
    private String status;
    private String delFlag;
    private String phone;
    private Integer phoneVerified;
}
