package org.dromara.fanghu.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime; import lombok.Data;
@Data @TableName("fh_payment_transaction")
public class PaymentTransaction extends FanghuEntity { private Long orderId; private String channel; private String providerTransactionNo; private String eventId; private String eventType; private String status; private String payload; }
