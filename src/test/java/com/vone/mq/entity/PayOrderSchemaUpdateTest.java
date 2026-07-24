package com.vone.mq.entity;

import com.vone.mq.utils.PayOrderSchemaMigration;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.persistence.Column;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

public class PayOrderSchemaUpdateTest {

    private static final int CALLBACK_URL_LENGTH = 2048;

    @Test
    public void schemaUpdateWidensExistingCallbackUrlColumns() throws Exception {
        String databaseUrl = "jdbc:h2:mem:pay-order-schema-update;DB_CLOSE_DELAY=-1";
        createLegacySchema(databaseUrl);

        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(databaseUrl, "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        PayOrderSchemaMigration migration =
                new PayOrderSchemaMigration(new JdbcTemplate(dataSource));

        migration.run(null);
        migration.run(null);

        assertColumnLength(databaseUrl, "NOTIFY_URL", CALLBACK_URL_LENGTH);
        assertColumnLength(databaseUrl, "RETURN_URL", CALLBACK_URL_LENGTH);
        insertOrderWithLongCallbackUrls(databaseUrl);
    }

    @Test
    public void entityCreatesCallbackUrlColumnsAtExpandedLength() throws Exception {
        assertEquals(
                CALLBACK_URL_LENGTH,
                PayOrder.class.getDeclaredField("notifyUrl")
                        .getAnnotation(Column.class)
                        .length());
        assertEquals(
                CALLBACK_URL_LENGTH,
                PayOrder.class.getDeclaredField("returnUrl")
                        .getAnnotation(Column.class)
                        .length());
    }

    private void createLegacySchema(String databaseUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE pay_order ("
                    + "id BIGINT PRIMARY KEY,"
                    + "order_id VARCHAR(255),"
                    + "pay_id VARCHAR(255),"
                    + "create_date BIGINT NOT NULL,"
                    + "pay_date BIGINT NOT NULL,"
                    + "close_date BIGINT NOT NULL,"
                    + "param VARCHAR(255),"
                    + "type INTEGER NOT NULL,"
                    + "price DOUBLE NOT NULL,"
                    + "really_price DOUBLE NOT NULL,"
                    + "notify_url VARCHAR(255),"
                    + "return_url VARCHAR(255),"
                    + "state INTEGER NOT NULL,"
                    + "is_auto INTEGER NOT NULL,"
                    + "pay_url VARCHAR(255))");
        }
    }

    private void insertOrderWithLongCallbackUrls(String databaseUrl) throws Exception {
        String longUrl = "https://example.com/callback?" + repeat("a", 400);
        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO pay_order ("
                             + "id, order_id, pay_id, create_date, pay_date, close_date, param, "
                             + "type, price, really_price, notify_url, return_url, state, is_auto, "
                             + "pay_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, 1L);
            statement.setString(2, "order-1");
            statement.setString(3, "pay-1");
            statement.setLong(4, 0L);
            statement.setLong(5, 0L);
            statement.setLong(6, 0L);
            statement.setString(7, "");
            statement.setInt(8, 2);
            statement.setDouble(9, 1.0D);
            statement.setDouble(10, 1.0D);
            statement.setString(11, longUrl);
            statement.setString(12, longUrl);
            statement.setInt(13, 0);
            statement.setInt(14, 1);
            statement.setString(15, "");
            assertEquals(1, statement.executeUpdate());
        }
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    private void assertColumnLength(
            String databaseUrl, String columnName, int expectedLength) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT CHARACTER_MAXIMUM_LENGTH "
                             + "FROM INFORMATION_SCHEMA.COLUMNS "
                             + "WHERE TABLE_NAME = 'PAY_ORDER' "
                             + "AND COLUMN_NAME = '" + columnName + "'")) {
            result.next();
            assertEquals(expectedLength, result.getInt(1));
        }
    }
}
