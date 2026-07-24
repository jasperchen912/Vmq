package com.vone.mq.service;

import com.vone.mq.dao.PayOrderDao;
import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.dao.SettingDao;
import com.vone.mq.dao.TmpPriceDao;
import com.vone.mq.dto.CommonRes;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AdminServiceDashboardTest {

    @Test
    void dashboardUsesOneConditionalAggregateWithoutChangingTotals() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dashboard;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE pay_order (
                  id BIGINT PRIMARY KEY,
                  create_date BIGINT NOT NULL,
                  state INTEGER NOT NULL,
                  price DOUBLE NOT NULL
                )
                """);

        long today = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() + 1_000;
        long yesterday = today - 86_400_000L;
        jdbcTemplate.update(
                "INSERT INTO pay_order (id, create_date, state, price) VALUES (?, ?, ?, ?)",
                1L, today, 1, 10D);
        jdbcTemplate.update(
                "INSERT INTO pay_order (id, create_date, state, price) VALUES (?, ?, ?, ?)",
                2L, today, 2, 5.5D);
        jdbcTemplate.update(
                "INSERT INTO pay_order (id, create_date, state, price) VALUES (?, ?, ?, ?)",
                3L, today, -1, 7D);
        jdbcTemplate.update(
                "INSERT INTO pay_order (id, create_date, state, price) VALUES (?, ?, ?, ?)",
                4L, yesterday, 1, 10D);

        AdminService service = new AdminService(
                mock(SettingDao.class),
                mock(PayOrderDao.class),
                mock(TmpPriceDao.class),
                mock(PayQrcodeDao.class),
                jdbcTemplate);

        CommonRes response = service.getMain();
        @SuppressWarnings("unchecked")
        Map<String, String> values = (Map<String, String>) response.getData();

        assertEquals("3", values.get("todayOrder"));
        assertEquals("2", values.get("todaySuccessOrder"));
        assertEquals("1", values.get("todayCloseOrder"));
        assertEquals("15.5", values.get("todayMoney"));
        assertEquals("2", values.get("countOrder"));
        assertEquals("25.5", values.get("countMoney"));
    }
}
