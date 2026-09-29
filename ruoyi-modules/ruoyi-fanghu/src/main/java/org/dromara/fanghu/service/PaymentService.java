package org.dromara.fanghu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.fanghu.domain.PaymentOrder;
import org.dromara.fanghu.domain.PaymentRefund;
import org.dromara.fanghu.domain.PaymentTransaction;
import org.dromara.fanghu.domain.ProductPlan;
import org.dromara.fanghu.domain.SupplierCompany;
import org.dromara.fanghu.domain.SupplierMembership;
import org.dromara.fanghu.mapper.PaymentOrderMapper;
import org.dromara.fanghu.mapper.PaymentRefundMapper;
import org.dromara.fanghu.mapper.PaymentTransactionMapper;
import org.dromara.fanghu.mapper.ProductPlanMapper;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.fanghu.mapper.SupplierMembershipMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PaymentService {
    private final ProductPlanMapper plans;
    private final PaymentOrderMapper orders;
    private final PaymentTransactionMapper transactions;
    private final PaymentRefundMapper refunds;
    private final SupplierMapper suppliers;
    private final SupplierMembershipMapper memberships;

    public List<ProductPlan> activePlans() {
        return plans.selectList(new LambdaQueryWrapper<ProductPlan>().eq(ProductPlan::getEnabled, true).orderByAsc(ProductPlan::getPrice));
    }

    public List<PaymentOrder> ordersFor(Long userId, boolean admin) {
        return orders.selectList(new LambdaQueryWrapper<PaymentOrder>().eq(!admin, PaymentOrder::getUserId, userId).orderByDesc(PaymentOrder::getCreatedAt));
    }

    @Transactional
    public PaymentOrder createOrder(Long userId, Long supplierId, Long planId, String channel, String requestId) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("clientRequestId不能为空");
        PaymentOrder existing = orders.selectOne(new LambdaQueryWrapper<PaymentOrder>().eq(PaymentOrder::getUserId, userId).eq(PaymentOrder::getClientRequestId, requestId));
        if (existing != null) return existing;
        SupplierCompany supplier = suppliers.selectById(supplierId);
        ProductPlan plan = plans.selectById(planId);
        if (supplier == null || !userId.equals(supplier.getOwnerUserId())) throw new IllegalArgumentException("无权为该供应商下单");
        if (!"APPROVED".equals(supplier.getVerificationStatus())) throw new IllegalArgumentException("企业资质审核通过后才能购买套餐");
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) throw new IllegalArgumentException("套餐不存在或已下架");
        String normalizedChannel = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WECHAT", "ALIPAY").contains(normalizedChannel)) throw new IllegalArgumentException("仅支持 WECHAT 或 ALIPAY 模拟支付");
        boolean active = memberships.selectCount(new LambdaQueryWrapper<SupplierMembership>().eq(SupplierMembership::getSupplierId, supplierId).eq(SupplierMembership::getStatus, "ACTIVE").and(q -> q.isNull(SupplierMembership::getEndsAt).or().gt(SupplierMembership::getEndsAt, LocalDateTime.now()))) > 0;
        if (active) throw new IllegalArgumentException("当前企业已有生效中的会员服务");
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo("FH" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        order.setUserId(userId); order.setSupplierId(supplierId); order.setPlanId(planId); order.setChannel(normalizedChannel);
        order.setAmount(plan.getPrice()); order.setCurrency(plan.getCurrency()); order.setStatus("CREATED"); order.setClientRequestId(requestId);
        order.setRefundStatus("NONE"); order.setExpiredAt(LocalDateTime.now().plusMinutes(30));
        orders.insert(order); return order;
    }

    @Transactional
    public PaymentOrder markPaid(String orderNo, String eventId, String providerTxn, String payload) {
        PaymentOrder order = required(orderNo);
        PaymentTransaction seen = transactions.selectOne(new LambdaQueryWrapper<PaymentTransaction>().eq(PaymentTransaction::getChannel, order.getChannel()).eq(PaymentTransaction::getEventId, eventId));
        if (seen != null) return order;
        if ("REFUNDED".equals(order.getStatus()) || "CLOSED".equals(order.getStatus())) throw new IllegalArgumentException("订单已关闭，不能支付");
        if ("PAID".equals(order.getStatus())) return order;
        ProductPlan plan = plans.selectById(order.getPlanId());
        order.setStatus("PAID"); order.setProviderTransactionNo(providerTxn); order.setPaidAt(LocalDateTime.now()); orders.updateById(order);
        PaymentTransaction transaction = new PaymentTransaction(); transaction.setOrderId(order.getId()); transaction.setChannel(order.getChannel()); transaction.setProviderTransactionNo(providerTxn); transaction.setEventId(eventId); transaction.setEventType("PAYMENT_SUCCESS"); transaction.setStatus("SUCCESS"); transaction.setPayload(payload); transactions.insert(transaction);
        SupplierMembership membership = memberships.selectOne(new LambdaQueryWrapper<SupplierMembership>().eq(SupplierMembership::getSupplierId, order.getSupplierId()).eq(SupplierMembership::getStatus, "PENDING").orderByDesc(SupplierMembership::getCreatedAt).last("LIMIT 1"));
        if (membership == null) membership = new SupplierMembership();
        membership.setSupplierId(order.getSupplierId()); membership.setSourceType("PAID"); membership.setPlanName(plan.getName()); membership.setFeeAmount(order.getAmount()); membership.setPaymentStatus("PAID"); membership.setStatus("ACTIVE"); membership.setPlacementEnabled(true); membership.setStartsAt(LocalDateTime.now()); membership.setEndsAt(LocalDateTime.now().plusMonths(plan.getDurationMonths())); membership.setOperatorUserId(0L); membership.setNote("本地模拟支付已确认");
        if (membership.getId() == null) memberships.insert(membership); else memberships.updateById(membership);
        return order;
    }

    @Transactional
    public PaymentRefund requestRefund(String orderNo, BigDecimal amount, String reason) {
        PaymentOrder order = required(orderNo);
        if (!"PAID".equals(order.getStatus()) || "REFUNDED".equals(order.getRefundStatus())) throw new IllegalArgumentException("只有已支付订单可以退款");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(order.getAmount()) > 0) throw new IllegalArgumentException("退款金额不正确");
        PaymentRefund refund = new PaymentRefund(); refund.setOrderId(order.getId()); refund.setRefundNo("RF" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8)); refund.setAmount(amount); refund.setStatus("REQUESTED"); refund.setReason(reason); refund.setCreatedBy(LoginHelper.getUserId()); refunds.insert(refund);
        order.setStatus("REFUNDING"); order.setRefundStatus("REQUESTED"); orders.updateById(order); return refund;
    }

    @Transactional
    public PaymentRefund completeRefund(String refundNo, String eventId, String providerRefundNo) {
        PaymentRefund refund = refunds.selectOne(new LambdaQueryWrapper<PaymentRefund>().eq(PaymentRefund::getRefundNo, refundNo));
        if (refund == null) throw new IllegalArgumentException("退款单不存在");
        if ("COMPLETED".equals(refund.getStatus())) return refund;
        PaymentOrder order = orders.selectById(refund.getOrderId());
        PaymentTransaction seen = transactions.selectOne(new LambdaQueryWrapper<PaymentTransaction>().eq(PaymentTransaction::getChannel, order.getChannel()).eq(PaymentTransaction::getEventId, eventId));
        if (seen != null) return refund;
        refund.setStatus("COMPLETED"); refund.setProviderRefundNo(providerRefundNo); refund.setCompletedAt(LocalDateTime.now()); refunds.updateById(refund);
        order.setStatus("REFUNDED"); order.setRefundStatus("REFUNDED"); orders.updateById(order);
        memberships.selectList(new LambdaQueryWrapper<SupplierMembership>().eq(SupplierMembership::getSupplierId, order.getSupplierId()).eq(SupplierMembership::getStatus, "ACTIVE")).forEach(m -> { m.setStatus("CANCELLED"); m.setPlacementEnabled(false); memberships.updateById(m); });
        PaymentTransaction tx = new PaymentTransaction(); tx.setOrderId(order.getId()); tx.setChannel(order.getChannel()); tx.setProviderTransactionNo(providerRefundNo); tx.setEventId(eventId); tx.setEventType("REFUND_SUCCESS"); tx.setStatus("SUCCESS"); transactions.insert(tx); return refund;
    }

    public PaymentOrder required(String orderNo) { PaymentOrder order = orders.selectOne(new LambdaQueryWrapper<PaymentOrder>().eq(PaymentOrder::getOrderNo, orderNo)); if (order == null) throw new IllegalArgumentException("订单不存在"); return order; }
}
