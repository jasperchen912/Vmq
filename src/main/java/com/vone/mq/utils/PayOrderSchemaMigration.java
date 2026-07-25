package com.vone.mq.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PayOrderSchemaMigration implements SmartInitializingSingleton {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PayOrderSchemaMigration.class);
    static final int CALLBACK_URL_LENGTH = 2048;
    static final int ENTITY_SEQUENCE_ALLOCATION_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;

    public PayOrderSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {
        migrate();
    }

    public void migrate() {
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
        Long highestId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM " + tableName,
                Long.class);
        long normalizedHighestId = Math.max(
                0L,
                highestId == null ? 0L : highestId);
        long restartWith = Math.max(
                ENTITY_SEQUENCE_ALLOCATION_SIZE,
                normalizedHighestId
                        + ENTITY_SEQUENCE_ALLOCATION_SIZE);

        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS "
                        + sequenceName
                        + " START WITH "
                        + restartWith
                        + " INCREMENT BY "
                        + ENTITY_SEQUENCE_ALLOCATION_SIZE);
        jdbcTemplate.execute(
                "ALTER SEQUENCE "
                        + sequenceName
                        + " RESTART WITH "
                        + restartWith
                        + " INCREMENT BY "
                        + ENTITY_SEQUENCE_ALLOCATION_SIZE);
        LOGGER.info(
                "Aligned {} above {}.id={}; next generated ID will be {}",
                sequenceName,
                tableName,
                normalizedHighestId,
                normalizedHighestId + 1);
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
