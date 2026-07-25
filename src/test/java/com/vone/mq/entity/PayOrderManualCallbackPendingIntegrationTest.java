package com.vone.mq.entity;

import com.vone.mq.dao.PayOrderDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:manual-callback-pending-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "vmq.scheduler.enabled=false"
})
@Transactional
class PayOrderManualCallbackPendingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayOrderDao payOrderDao;

    @Test
    void transitionRevivesExpiredOrderOnceAndPreservesCloseTime() {
        PayOrder expired = order();
        expired = payOrderDao.saveAndFlush(expired);
        long confirmedAt = 30L;

        int updated = payOrderDao.markManualCallbackPending(
                expired.getId(), confirmedAt);
        int duplicate = payOrderDao.markManualCallbackPending(
                expired.getId(), confirmedAt + 1);

        Long id = expired.getId();
        assertAll(
                () -> assertEquals(1, updated),
                () -> assertEquals(0, duplicate),
                () -> assertEquals(2, jdbcTemplate.queryForObject(
                        "SELECT state FROM pay_order WHERE id=?",
                        Integer.class, id)),
                () -> assertEquals(confirmedAt, jdbcTemplate.queryForObject(
                        "SELECT pay_date FROM pay_order WHERE id=?",
                        Long.class, id)),
                () -> assertEquals(20L, jdbcTemplate.queryForObject(
                        "SELECT close_date FROM pay_order WHERE id=?",
                        Long.class, id)));
    }

    private PayOrder order() {
        PayOrder order = new PayOrder();
        order.setOrderId("expired-order");
        order.setPayId("expired-pay");
        order.setCreateDate(10L);
        order.setPayDate(0L);
        order.setCloseDate(20L);
        order.setParam("ORDER_PAYMENT");
        order.setType(2);
        order.setPrice(1D);
        order.setReallyPrice(1D);
        order.setNotifyUrl("http://callback.test/notice");
        order.setReturnUrl("");
        order.setState(-1);
        order.setIsAuto(1);
        order.setPayUrl("https://example.com/qr");
        return order;
    }
}
