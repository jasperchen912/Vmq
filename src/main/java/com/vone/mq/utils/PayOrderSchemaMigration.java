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
        alignEntitySequences();
        createPerformanceIndexes();
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

    private void createPerformanceIndexes() {
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pay_order_pay_id "
                        + "ON pay_order(pay_id)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pay_order_order_id "
                        + "ON pay_order(order_id)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pay_order_state_create_date "
                        + "ON pay_order(state, create_date)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pay_order_really_state_type "
                        + "ON pay_order(really_price, state, type)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pay_order_pay_date "
                        + "ON pay_order(pay_date)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_pay_order_type_state_id "
                        + "ON pay_order(type, state, id)");
        if (tableExists("PAY_QRCODE")) {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_pay_qrcode_price_type "
                            + "ON pay_qrcode(price, type)");
        }
    }

    private void alignEntitySequences() {
        alignSequenceAboveExistingIds("PAY_ORDER", "PAY_ORDER_SEQ");
        if (tableExists("PAY_QRCODE")) {
            alignSequenceAboveExistingIds("PAY_QRCODE", "PAY_QRCODE_SEQ");
        }
    }

    private void alignSequenceAboveExistingIds(String tableName, String sequenceName) {
        Long nextValue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM " + tableName,
                Long.class);
        long restartWith = nextValue == null ? 1L : Math.max(1L, nextValue);

        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS "
                        + sequenceName
                        + " START WITH "
                        + restartWith
                        + " INCREMENT BY 50");
        jdbcTemplate.execute(
                "ALTER SEQUENCE "
                        + sequenceName
                        + " RESTART WITH "
                        + restartWith);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }
}
