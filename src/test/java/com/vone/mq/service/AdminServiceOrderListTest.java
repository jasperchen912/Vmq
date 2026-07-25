package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.entity.PayOrder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceOrderListTest {

    @Test
    void ordersArePagedByCreationTimeThenIdDescending() {
        PayOrderDao payOrderDao = mock(PayOrderDao.class);
        when(payOrderDao.findAll(
                org.mockito.ArgumentMatchers.<Specification<PayOrder>>any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    Page<PayOrder> page = new PageImpl<>(
                            List.of(),
                            pageable,
                            0);
                    return page;
                });
        AdminService service = new AdminService(
                mock(SettingDao.class),
                payOrderDao,
                mock(TmpPriceDao.class),
                mock(PayQrcodeDao.class),
                mock(JdbcTemplate.class));

        service.getOrders(1, 10, null, null);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(payOrderDao).findAll(
                org.mockito.ArgumentMatchers.<Specification<PayOrder>>any(),
                pageableCaptor.capture());
        List<Sort.Order> orders = pageableCaptor
                .getValue()
                .getSort()
                .stream()
                .toList();

        assertEquals(2, orders.size());
        assertEquals("createDate", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("id", orders.get(1).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());
    }
}
