package com.fanghu.platform.domain; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data;
@Data @TableName("fh_demand") public class Demand extends BaseEntity { private Long customerId; private String title; private String description; private Long categoryId; private String budgetLevel; private String status; }
