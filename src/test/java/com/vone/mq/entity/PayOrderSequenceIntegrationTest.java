package com.vone.mq.entity;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.utils.PayOrderSchemaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pay-order-sequence-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "vmq.scheduler.enabled=false"
})
@Transactional
class PayOrderSequenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayOrderDao payOrderDao;

    @Autowired
    private PayOrderSchemaMigration migration;

    @Test
    void firstHibernateIdsFollowHighestMigratedIdWithoutCollision() {
        jdbcTemplate.update("""
                INSERT INTO pay_order (
                  id, order_id, pay_id, create_date, pay_date, close_date, param,
                  type, price, really_price, notify_url, return_url, state, is_auto,
                  pay_url
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                42L, "legacy-order", "legacy-pay", 1L, 0L, 0L, "",
                2, 1D, 1D, "", "", 0, 1, "");

        migration.migrate();

        PayOrder first = payOrderDao.saveAndFlush(order("new-pay-1", 2L));
        PayOrder second = payOrderDao.saveAndFlush(order("new-pay-2", 3L));

        assertEquals(43L, first.getId());
        assertEquals(44L, second.getId());
    }

    private PayOrder order(String payId, long createDate) {
        PayOrder order = new PayOrder();
        order.setOrderId("order-" + payId);
        order.setPayId(payId);
        order.setCreateDate(createDate);
        order.setPayDate(0L);
        order.setCloseDate(0L);
        order.setParam("");
        order.setType(2);
        order.setPrice(1D);
        order.setReallyPrice(1D);
        order.setNotifyUrl("");
        order.setReturnUrl("");
        order.setState(0);
        order.setIsAuto(1);
        order.setPayUrl("https://example.com/qr");
        return order;
    }
}
