package com.vone.mq.utils;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PayOrderSchemaMigration implements ApplicationRunner {

    static final int CALLBACK_URL_LENGTH = 2048;

    private final JdbcTemplate jdbcTemplate;

    public PayOrderSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        widenColumnIfNeeded("NOTIFY_URL");
        widenColumnIfNeeded("RETURN_URL");
    }

    private void widenColumnIfNeeded(String columnName) {
        List<Long> lengths = jdbcTemplate.queryForList(
                "SELECT CHARACTER_MAXIMUM_LENGTH "
                        + "FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' "
                        + "AND TABLE_NAME = 'PAY_ORDER' "
                        + "AND COLUMN_NAME = ?",
                Long.class,
                columnName);

        if (lengths.size() != 1) {
            throw new IllegalStateException(
                    "Expected one PAY_ORDER." + columnName + " column, found " + lengths.size());
        }

        Long currentLength = lengths.get(0);
        if (currentLength == null || currentLength < CALLBACK_URL_LENGTH) {
            jdbcTemplate.execute(
                    "ALTER TABLE pay_order ALTER COLUMN "
                            + columnName.toLowerCase()
                            + " VARCHAR("
                            + CALLBACK_URL_LENGTH
                            + ")");
        }
    }
}
