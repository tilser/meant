package com.meant.api.module.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@SpringBootTest
class MerchantIntegrationMigrationIT extends PostgresIntegrationTestSupport {

    private static final Instant CREATED_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-01T11:00:00Z");
    private static final Instant CAPTURED_AT = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationBackfillsLegacyMerchantAndIsIdempotentUnderLiquibase() throws Exception {
        String schema = "pcos002_" + UUID.randomUUID().toString().replace("-", "");
        UUID merchantRawId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String domain = "legacy-%s.example".formatted(UUID.randomUUID());
        String endpoint = "https://%s/api/ucp/mcp".formatted(domain);
        jdbcTemplate.execute("create schema " + schema);
        try {
            createLegacyTables(schema);
            insertLegacyMerchant(schema, merchantRawId, merchantId, domain, endpoint);

            runIntegrationMigration(schema);
            runIntegrationMigration(schema);

            Map<String, Object> integration = jdbcTemplate.queryForMap("""
                    select provider, kind, verified_domain, endpoint, protocol_version,
                           auth_strategy, status, source, raw_metadata::text as raw_metadata,
                           captured_at, created_at, updated_at
                    from %s.merchant_integration
                    where merchant_id = ?
                    """.formatted(schema), merchantId);
            List<String> roles = jdbcTemplate.queryForList("""
                    select role
                    from %s.merchant_integration_role
                    order by role
                    """.formatted(schema), String.class);

            assertThat(integration)
                    .containsEntry("provider", "GENERIC_UCP")
                    .containsEntry("kind", "MERCHANT_CONNECTION")
                    .containsEntry("verified_domain", domain)
                    .containsEntry("endpoint", endpoint)
                    .containsEntry("protocol_version", "2026-04-08")
                    .containsEntry("auth_strategy", "NONE")
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("source", "LEGACY_MERCHANT_BACKFILL")
                    .containsEntry("raw_metadata", "{\"legacy\": true}");
            assertThat(((Timestamp) integration.get("captured_at")).toInstant()).isEqualTo(CAPTURED_AT);
            assertThat(((Timestamp) integration.get("created_at")).toInstant()).isEqualTo(CREATED_AT);
            assertThat(((Timestamp) integration.get("updated_at")).toInstant()).isEqualTo(UPDATED_AT);
            assertThat(roles).containsExactly("CART", "CHECKOUT", "ORDERS", "STOREFRONT_CATALOG");
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.merchant where id = ?".formatted(schema),
                    Integer.class,
                    merchantId
            )).isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.merchant_integration where merchant_id = ?".formatted(schema),
                    Integer.class,
                    merchantId
            )).isOne();
            assertThat(jdbcTemplate.queryForObject("""
                    select indexdef
                    from pg_indexes
                    where schemaname = ?
                        and indexname = 'uk_merchant_integration_provider_verified_domain'
                    """, String.class, schema))
                    .contains("(lower(verified_domain), provider)");
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void migrationBackfillsEveryMerchantWhenNormalizedLegacyDomainsCollide() throws Exception {
        String schema = "pcos002_" + UUID.randomUUID().toString().replace("-", "");
        UUID olderMerchantId = UUID.randomUUID();
        UUID newerMerchantId = UUID.randomUUID();
        String domainRoot = "collision-%s.example".formatted(UUID.randomUUID());
        jdbcTemplate.execute("create schema " + schema);
        try {
            createLegacyTables(schema);
            insertLegacyMerchant(
                    schema,
                    UUID.randomUUID(),
                    olderMerchantId,
                    domainRoot.toUpperCase(),
                    "https://older.%s/api/ucp/mcp".formatted(domainRoot),
                    false,
                    CREATED_AT,
                    CREATED_AT
            );
            insertLegacyMerchant(
                    schema,
                    UUID.randomUUID(),
                    newerMerchantId,
                    domainRoot + ".",
                    "https://newer.%s/api/ucp/mcp".formatted(domainRoot),
                    true,
                    UPDATED_AT,
                    UPDATED_AT
            );

            runIntegrationMigration(schema);

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.merchant_integration".formatted(schema),
                    Integer.class
            )).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.merchant_integration where verified_domain = ?".formatted(schema),
                    Integer.class,
                    domainRoot
            )).isOne();
            assertThat(jdbcTemplate.queryForObject(
                    "select merchant_id from %s.merchant_integration where verified_domain = ?".formatted(schema),
                    UUID.class,
                    domainRoot
            )).isEqualTo(newerMerchantId);
            assertThat(jdbcTemplate.queryForObject(
                    "select verified_domain from %s.merchant_integration where merchant_id = ?".formatted(schema),
                    String.class,
                    olderMerchantId
            )).isNull();
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    private void createLegacyTables(String schema) {
        jdbcTemplate.execute("""
                create table %s.merchant_raw (
                    id uuid primary key,
                    has_cart_management boolean not null,
                    has_checkout boolean not null,
                    has_order boolean not null
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.merchant (
                    id uuid primary key,
                    merchant_raw_id uuid not null references %s.merchant_raw(id),
                    domain text not null,
                    ucp_url text not null,
                    ucp_version text not null,
                    advertised_mcp_endpoint text,
                    profile_mcp_endpoint text,
                    profile_raw jsonb,
                    profile_captured_at timestamp with time zone,
                    active boolean not null,
                    last_profiled_at timestamp with time zone not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """.formatted(schema, schema));
    }

    private void insertLegacyMerchant(
            String schema,
            UUID merchantRawId,
            UUID merchantId,
            String domain,
            String endpoint
    ) {
        insertLegacyMerchant(
                schema,
                merchantRawId,
                merchantId,
                domain,
                endpoint,
                true,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private void insertLegacyMerchant(
            String schema,
            UUID merchantRawId,
            UUID merchantId,
            String domain,
            String endpoint,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        jdbcTemplate.update(
                "insert into %s.merchant_raw values (?, true, true, true)".formatted(schema),
                merchantRawId
        );
        jdbcTemplate.update("""
                insert into %s.merchant (
                    id, merchant_raw_id, domain, ucp_url, ucp_version,
                    advertised_mcp_endpoint, profile_mcp_endpoint, profile_raw,
                    profile_captured_at, active, last_profiled_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """.formatted(schema),
                merchantId,
                merchantRawId,
                domain,
                "https://%s/.well-known/ucp".formatted(domain),
                "2026-04-08",
                endpoint,
                "https://%s/fallback/mcp".formatted(domain),
                "{\"legacy\":true}",
                Timestamp.from(CAPTURED_AT),
                active,
                Timestamp.from(updatedAt),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
        );
    }

    private void runIntegrationMigration(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             AutoCloseable ignored = selectSchema(connection, schema)) {
            SingleConnectionDataSource schemaDataSource = new SingleConnectionDataSource(connection, true);
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(schemaDataSource);
            liquibase.setChangeLog("classpath:db/changelog/migration/029-add-merchant-integration.xml");
            liquibase.setDefaultSchema(schema);
            liquibase.setLiquibaseSchema(schema);
            liquibase.afterPropertiesSet();
        }
    }

    private AutoCloseable selectSchema(Connection connection, String schema) throws SQLException {
        String currentSchema = connection.getSchema();
        String schemaToRestore = currentSchema == null || currentSchema.isBlank() ? "public" : currentSchema;
        connection.setSchema(schema);
        return () -> connection.setSchema(schemaToRestore);
    }
}
