package org.dromara.fanghu.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal; import java.time.LocalDateTime;
import lombok.Data;
@Data @TableName("fh_payment_order")
public class PaymentOrder extends FanghuEntity { private String orderNo; private Long userId; private Long supplierId; private Long planId; private String channel; private BigDecimal amount; private String currency; private String status; private String clientRequestId; private String providerTransactionNo; private LocalDateTime paidAt; private LocalDateTime expiredAt; private LocalDateTime closedAt; private String refundStatus; }
