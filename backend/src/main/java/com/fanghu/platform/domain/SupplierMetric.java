package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("fh_supplier_metric")
public class SupplierMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private LocalDate metricDate;
    private Integer impressions;
    private Integer views;
    private Integer inquiries;
    private Integer downloads;
    private Integer qualifiedLeads;
    private Integer wonLeads;
}
