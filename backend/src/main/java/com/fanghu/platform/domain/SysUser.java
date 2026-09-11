package com.fanghu.platform.domain;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("sys_user") public class SysUser { @TableId(type=IdType.AUTO) private Long userId; private String userName; private String password; private String status; }
