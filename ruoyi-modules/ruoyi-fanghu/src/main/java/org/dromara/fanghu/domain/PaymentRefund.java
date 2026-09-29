package org.dromara.fanghu.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal; import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("fh_payment_refund")
public class PaymentRefund extends FanghuEntity { private Long orderId; private String refundNo; private BigDecimal amount; private String status; private String reason; private String providerRefundNo; private Long createdBy; private LocalDateTime completedAt; }
