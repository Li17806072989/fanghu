package org.dromara.fanghu.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import java.math.BigDecimal;
import java.util.List;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.fanghu.domain.PaymentOrder;
import org.dromara.fanghu.domain.PaymentRefund;
import org.dromara.fanghu.domain.ProductPlan;
import org.dromara.fanghu.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;
    private final boolean mockEnabled;
    public PaymentController(PaymentService service, @Value("${fanghu.payment.mock-enabled:true}") boolean mockEnabled) { this.service = service; this.mockEnabled = mockEnabled; }

    @GetMapping("/plans") public List<ProductPlan> plans() { return service.activePlans(); }

    @GetMapping("/orders") public List<PaymentOrder> orders() { return service.ordersFor(LoginHelper.getUserId(), isAdmin()); }

    @SaCheckRole(value={"supplier","admin","superadmin"}, mode=SaMode.OR)
    @PostMapping("/orders")
    public PaymentOrder create(@RequestBody CreateOrder request) {
        if (request == null) throw new IllegalArgumentException("订单参数不能为空");
        Long supplierId = request.supplierId();
        if (!isAdmin()) supplierId = supplierId;
        return service.createOrder(LoginHelper.getUserId(), supplierId, request.planId(), request.channel(), request.clientRequestId());
    }

    @SaCheckRole(value={"supplier","admin","superadmin"}, mode=SaMode.OR)
    @PostMapping("/orders/{orderNo}/mock-pay")
    public PaymentOrder mockPay(@PathVariable String orderNo, @RequestBody(required=false) MockCallback callback) {
        ensureMock();
        return service.markPaid(orderNo, callback == null ? "MOCK-" + orderNo : callback.eventId(), callback == null ? "MOCK-TXN-" + orderNo : callback.providerTransactionNo(), callback == null ? "{}" : callback.payload());
    }

    @PostMapping("/callbacks/{channel}")
    public PaymentOrder callback(@PathVariable String channel, @RequestBody MockCallback callback) {
        ensureMock();
        if (callback == null || callback.orderNo() == null || callback.eventId() == null) throw new IllegalArgumentException("回调参数不完整");
        PaymentOrder order = service.required(callback.orderNo());
        if (!order.getChannel().equalsIgnoreCase(channel)) throw new IllegalArgumentException("支付渠道不匹配");
        return service.markPaid(callback.orderNo(), callback.eventId(), callback.providerTransactionNo(), callback.payload());
    }

    @SaCheckPermission("platform:write")
    @PostMapping("/orders/{orderNo}/refund")
    public PaymentRefund refund(@PathVariable String orderNo, @RequestBody RefundRequest request) { return service.requestRefund(orderNo, request.amount(), request.reason()); }

    @SaCheckPermission("platform:write")
    @PostMapping("/refunds/{refundNo}/mock-complete")
    public PaymentRefund mockRefund(@PathVariable String refundNo, @RequestBody(required=false) RefundCallback callback) { ensureMock(); return service.completeRefund(refundNo, callback == null ? "MOCK-REFUND-" + refundNo : callback.eventId(), callback == null ? "MOCK-RF-" + refundNo : callback.providerRefundNo()); }

    @SaCheckPermission("platform:write")
    @PatchMapping("/orders/{orderNo}/confirm-paid")
    public PaymentOrder confirmPaid(@PathVariable String orderNo, @RequestBody(required=false) MockCallback callback) { return service.markPaid(orderNo, "MANUAL-" + orderNo, callback == null ? "MANUAL" : callback.providerTransactionNo(), "manual confirmation"); }

    private void ensureMock() { if (!mockEnabled) throw new IllegalStateException("本地模拟支付已关闭"); }
    private boolean isAdmin() { return LoginHelper.isSuperAdmin() || StpUtil.hasRole("admin"); }
    public record CreateOrder(Long supplierId, Long planId, String channel, String clientRequestId) { }
    public record MockCallback(String orderNo, String eventId, String providerTransactionNo, String payload) { }
    public record RefundRequest(BigDecimal amount, String reason) { }
    public record RefundCallback(String eventId, String providerRefundNo) { }
}
