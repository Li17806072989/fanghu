package org.dromara.fanghu.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.fanghu.domain.PaymentOrder;
import org.dromara.fanghu.domain.PaymentTransaction;
import org.dromara.fanghu.mapper.PaymentOrderMapper;
import org.dromara.fanghu.mapper.PaymentRefundMapper;
import org.dromara.fanghu.mapper.PaymentTransactionMapper;
import org.dromara.fanghu.mapper.ProductPlanMapper;
import org.dromara.fanghu.mapper.SupplierMapper;
import org.dromara.fanghu.mapper.SupplierMembershipMapper;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
    @Test
    void repeatedCallbackIsIdempotentWhenEventAlreadyExists() {
        PaymentOrder order = new PaymentOrder(); order.setOrderNo("FH1"); order.setChannel("ALIPAY"); order.setStatus("PAID");
        PaymentOrderMapper orders = mock(PaymentOrderMapper.class); when(orders.selectOne(any(Wrapper.class))).thenReturn(order);
        PaymentTransactionMapper transactions = mock(PaymentTransactionMapper.class); when(transactions.selectOne(any(Wrapper.class))).thenReturn(new PaymentTransaction());
        PaymentService service = new PaymentService(mock(ProductPlanMapper.class), orders, transactions, mock(PaymentRefundMapper.class), mock(SupplierMapper.class), mock(SupplierMembershipMapper.class));
        assertSame(order, service.markPaid("FH1", "evt-1", "txn-1", "{}"));
        verify(transactions, never()).insert(any(PaymentTransaction.class));
    }
}
