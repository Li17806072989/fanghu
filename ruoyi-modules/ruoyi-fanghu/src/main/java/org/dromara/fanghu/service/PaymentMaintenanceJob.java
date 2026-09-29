package org.dromara.fanghu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.dromara.fanghu.domain.PaymentOrder;
import org.dromara.fanghu.domain.SupplierMembership;
import org.dromara.fanghu.mapper.PaymentOrderMapper;
import org.dromara.fanghu.mapper.SupplierMembershipMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PaymentMaintenanceJob {
    private final SupplierMembershipMapper memberships;
    private final PaymentOrderMapper orders;

    @Scheduled(fixedDelayString = "${fanghu.payment.expiry-check-ms:300000}")
    public void expire() {
        memberships.selectList(new LambdaQueryWrapper<SupplierMembership>().eq(SupplierMembership::getStatus, "ACTIVE").lt(SupplierMembership::getEndsAt, LocalDateTime.now()))
            .forEach(item -> { item.setStatus("EXPIRED"); item.setPlacementEnabled(false); memberships.updateById(item); });
        orders.selectList(new LambdaQueryWrapper<PaymentOrder>().in(PaymentOrder::getStatus, "CREATED", "PAYING").lt(PaymentOrder::getExpiredAt, LocalDateTime.now()))
            .forEach(item -> { item.setStatus("CLOSED"); item.setClosedAt(LocalDateTime.now()); orders.updateById(item); });
    }
}
