package com.meant.api.module.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@SpringBootTest
class CheckoutLifecycleMigrationIT extends PostgresIntegrationTestSupport {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upgradeAddsLifecycleMetadataClearsOnlyShopifyRawPayloadAndIsIdempotent() throws Exception {
        String schema = "pcos014_" + UUID.randomUUID().toString().replace("-", "");
        UUID shopify = UUID.randomUUID();
        UUID generic = UUID.randomUUID();
        UUID providerBoundGeneric = UUID.randomUUID();
        jdbcTemplate.execute("create schema " + schema);
        try {
            jdbcTemplate.execute("""
                    create table %s.cart (
                        id uuid primary key,
                        provider text,
                        routing_scope_key text,
                        checkout_id text,
                        checkout_status text,
                        raw_checkout_response text
                    )
                    """.formatted(schema));
            jdbcTemplate.update("insert into %s.cart values (?, 'SHOPIFY', 'SHOPIFY:external', ?, ?, ?)".formatted(schema),
                    shopify, "stale-checkout", "processing", "{\"buyer\":\"prohibited\",\"payment\":\"prohibited\"}");
            jdbcTemplate.update("insert into %s.cart values (?, 'GENERIC_UCP', 'LEGACY:merchant:1', ?, ?, ?)".formatted(schema),
                    generic, "legacy-checkout", "processing", "{\"legacy\":true}");
            jdbcTemplate.update("insert into %s.cart values (?, 'GENERIC_UCP', 'GENERIC_UCP:integration:1', ?, ?, ?)"
                            .formatted(schema), providerBoundGeneric, "stale-generic-checkout", "processing",
                    "{\"buyer\":\"prohibited\"}");

            runMigration(schema);
            runMigration(schema);

            assertThat(jdbcTemplate.queryForObject(
                    "select raw_checkout_response from %s.cart where id = ?".formatted(schema),
                    String.class, shopify)).isNull();
            assertThat(jdbcTemplate.queryForMap(
                    "select checkout_id, checkout_status from %s.cart where id = ?".formatted(schema), shopify))
                    .containsEntry("checkout_id", null)
                    .containsEntry("checkout_status", null);
            assertThat(jdbcTemplate.queryForObject(
                    "select raw_checkout_response from %s.cart where id = ?".formatted(schema),
                    String.class, generic)).isEqualTo("{\"legacy\":true}");
            assertThat(jdbcTemplate.queryForMap(
                    "select checkout_id, checkout_status from %s.cart where id = ?".formatted(schema), generic))
                    .containsEntry("checkout_id", "legacy-checkout")
                    .containsEntry("checkout_status", "processing");
            assertThat(jdbcTemplate.queryForObject(
                    "select raw_checkout_response from %s.cart where id = ?".formatted(schema),
                    String.class, providerBoundGeneric)).isNull();
            assertThat(jdbcTemplate.queryForMap(
                    "select checkout_id, checkout_status from %s.cart where id = ?".formatted(schema),
                    providerBoundGeneric))
                    .containsEntry("checkout_id", null)
                    .containsEntry("checkout_status", null);
            assertThat(jdbcTemplate.queryForList("""
                    select column_name from information_schema.columns
                    where table_schema = ? and table_name = 'cart'
                    and column_name in ('checkout_protocol_version', 'checkout_lifecycle_state',
                                        'checkout_synchronized_at')
                    """, String.class, schema)).containsExactlyInAnyOrder(
                    "checkout_protocol_version", "checkout_lifecycle_state", "checkout_synchronized_at");
            assertThat(jdbcTemplate.update(
                    "update %s.cart set checkout_lifecycle_state = 'MERCHANT_HANDOFF_REQUIRED' where id = ?"
                            .formatted(schema), shopify)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select checkout_lifecycle_state from %s.cart where id = ?".formatted(schema),
                    String.class, shopify)).isEqualTo("MERCHANT_HANDOFF_REQUIRED");
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "update %s.cart set raw_checkout_response = '{}' where id = ?".formatted(schema), shopify))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "update %s.cart set checkout_id = 'checkout-1', checkout_lifecycle_state = 'INVALID' where id = ?"
                            .formatted(schema), shopify))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "update %s.cart set checkout_id = 'checkout-1' where id = ?".formatted(schema), shopify))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    private void runMigration(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             AutoCloseable ignored = selectSchema(connection, schema)) {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(new SingleConnectionDataSource(connection, true));
            liquibase.setChangeLog("classpath:db/changelog/migration/036-add-checkout-lifecycle-metadata.xml");
            liquibase.setDefaultSchema(schema);
            liquibase.setLiquibaseSchema(schema);
            liquibase.afterPropertiesSet();
            liquibase.setChangeLog(
                    "classpath:db/changelog/migration/056-allow-merchant-handoff-checkout-lifecycle.xml");
            liquibase.afterPropertiesSet();
        }
    }

    private AutoCloseable selectSchema(Connection connection, String schema) throws SQLException {
        String current = connection.getSchema();
        connection.setSchema(schema);
        return () -> connection.setSchema(current == null || current.isBlank() ? "public" : current);
    }
}
