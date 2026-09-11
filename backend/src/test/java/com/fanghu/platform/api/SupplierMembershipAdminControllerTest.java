package com.fanghu.platform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fanghu.platform.domain.SupplierCompany;
import com.fanghu.platform.domain.SupplierMembership;
import com.fanghu.platform.mapper.SupplierMapper;
import com.fanghu.platform.mapper.SupplierMembershipMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplierMembershipAdminControllerTest {
    @Test
    void returnsEmptyPageWithoutBatchLoadingSuppliers() {
        SupplierMembershipMapper memberships = mock(SupplierMembershipMapper.class);
        SupplierMapper suppliers = mock(SupplierMapper.class);
        SupplierCompany supplier = new SupplierCompany();
        supplier.setId(1L);
        supplier.setName("测试企业");
        Page<SupplierMembership> emptyPage = Page.of(1, 20, 0);
        emptyPage.setRecords(List.of());

        when(memberships.selectList(any())).thenReturn(List.of());
        when(suppliers.selectList(any())).thenReturn(List.of(supplier));
        when(memberships.selectPage(any(), any())).thenReturn(emptyPage);

        SupplierMembershipAdminController controller = new SupplierMembershipAdminController(memberships, suppliers);
        SupplierMembershipAdminController.MembershipPage result = controller.list(1, 20, null, null);

        assertEquals(0, result.total());
        assertEquals(List.of(), result.records());
        verify(suppliers, never()).selectBatchIds(any());
    }
}
