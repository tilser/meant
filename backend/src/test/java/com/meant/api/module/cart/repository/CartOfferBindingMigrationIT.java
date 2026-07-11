package com.meant.api.module.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@SpringBootTest
class CartOfferBindingMigrationIT extends PostgresIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upgradesLegacyCartSchemaWithoutLosingRowsAndAllowsExternalMerchantScope() throws Exception {
        String schema = "pcos013_" + UUID.randomUUID().toString().replace("-", "");
        UUID cartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        jdbcTemplate.execute("create schema " + schema);
        try {
            createLegacyTables(schema);
            insertLegacyCart(schema, cartId, lineId);

            runMigration(schema);
            runMigration(schema);

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.cart where id = ?".formatted(schema), Integer.class, cartId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.cart_line where id = ?".formatted(schema), Integer.class, lineId))
                    .isEqualTo(1);
            assertThat(nullableColumns(schema, "cart", List.of("merchant_id", "merchant_domain")))
                    .containsExactlyInAnyOrder("merchant_domain", "merchant_id");
            assertThat(columns(schema, "cart", List.of(
                    "provider", "merchant_integration_id", "external_merchant_id", "routing_scope_key")))
                    .containsExactlyInAnyOrder(
                            "provider", "merchant_integration_id", "external_merchant_id", "routing_scope_key");
            assertThat(columns(schema, "cart_line", List.of(
                    "provider", "merchant_integration_id", "external_merchant_id", "external_product_id",
                    "external_variant_id", "offer_product_id", "offer_variant_id", "offer_key", "source_type",
                    "source_identity", "selected_options_json", "components_json", "selling_plan_json", "selected_at")))
                    .hasSize(14);
            assertThat(checkConstraints(schema)).contains(
                    "ck_cart_legacy_or_bound_scope", "ck_cart_line_offer_binding_shape");
            assertThat(nullableColumns(schema, "merchant_raw", List.of("dataset_row_idx")))
                    .containsExactly("dataset_row_idx");
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "update %s.cart set provider = 'SHOPIFY' where id = ?".formatted(schema), cartId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("""
                    update %s.cart_line set offer_key = 'partial' where id = ?
                    """.formatted(schema), lineId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    private void createLegacyTables(String schema) {
        jdbcTemplate.execute("create table %s.merchant_integration (id uuid primary key)".formatted(schema));
        jdbcTemplate.execute("""
                create table %s.merchant_raw (
                    id uuid primary key,
                    dataset_row_idx integer not null unique
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.cart (
                    id uuid primary key,
                    user_id uuid not null,
                    merchant_id uuid not null,
                    merchant_domain text not null
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.cart_line (
                    id uuid primary key,
                    cart_id uuid not null,
                    remote_cart_line_id text not null,
                    product_variant_id text not null
                )
                """.formatted(schema));
    }

    private void insertLegacyCart(String schema, UUID cartId, UUID lineId) {
        jdbcTemplate.update(
                "insert into %s.cart (id, user_id, merchant_id, merchant_domain) values (?, ?, ?, ?)"
                        .formatted(schema),
                cartId, UUID.randomUUID(), UUID.randomUUID(), "legacy.example");
        jdbcTemplate.update(
                "insert into %s.cart_line (id, cart_id, remote_cart_line_id, product_variant_id) values (?, ?, ?, ?)"
                        .formatted(schema),
                lineId, cartId, "legacy-line", "legacy-variant");
    }

    private List<String> nullableColumns(String schema, String table, List<String> expected) {
        return jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = ? and table_name = ? and is_nullable = 'YES'
                """, String.class, schema, table).stream().filter(expected::contains).toList();
    }

    private List<String> columns(String schema, String table, List<String> expected) {
        return jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = ? and table_name = ?
                """, String.class, schema, table).stream().filter(expected::contains).toList();
    }

    private List<String> checkConstraints(String schema) {
        return jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where constraint_schema = ? and constraint_type = 'CHECK'
                """, String.class, schema);
    }

    private void runMigration(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             AutoCloseable ignored = selectSchema(connection, schema)) {
            SingleConnectionDataSource schemaDataSource = new SingleConnectionDataSource(connection, true);
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(schemaDataSource);
            liquibase.setChangeLog("classpath:db/changelog/migration/035-bind-cart-offer-scope.xml");
            liquibase.setDefaultSchema(schema);
            liquibase.setLiquibaseSchema(schema);
            liquibase.afterPropertiesSet();
        }
    }

    private AutoCloseable selectSchema(Connection connection, String schema) throws SQLException {
        String current = connection.getSchema();
        connection.setSchema(schema);
        return () -> connection.setSchema(current == null || current.isBlank() ? "public" : current);
    }
}
