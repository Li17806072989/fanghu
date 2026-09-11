package com.fanghu.platform.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fh_supplier_membership")
public class SupplierMembership extends BaseEntity {
    private Long supplierId;
    private String sourceType;
    private String planName;
    private BigDecimal feeAmount;
    private String paymentStatus;
    private String status;
    private Boolean placementEnabled;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Long operatorUserId;
    private String note;
}
