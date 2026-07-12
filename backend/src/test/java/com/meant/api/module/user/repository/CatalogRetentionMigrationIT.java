package com.meant.api.module.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@SpringBootTest
class CatalogRetentionMigrationIT extends PostgresIntegrationTestSupport {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationErasesHistoricalSavedPayloadAndMakesLegacySnapshotColumnsNullable() throws Exception {
        String schema = "pcos010_" + UUID.randomUUID().toString().replace("-", "");
        UUID savedId = UUID.randomUUID();
        jdbcTemplate.execute("create schema " + schema);
        try {
            createLegacyTables(schema);
            insertLegacySavedProduct(schema, savedId);

            runMigration(schema);

            Map<String, Object> payload = jdbcTemplate.queryForMap("""
                    select product_hash, name, brand, category, tone, image_url, product_url, remote,
                           match_score, price_from, merchant_count, satisfies, misses, note, pros, cons,
                           review_score, review_count, review_insight, offers, needs, provides,
                           reference_verified_at
                    from %s.user_saved_products where id = ?
                    """.formatted(schema), savedId);
            assertThat(payload.values()).containsOnlyNulls();
            List<String> nonNullablePayloadColumns = jdbcTemplate.queryForList("""
                    select column_name
                    from information_schema.columns
                    where table_schema = ? and table_name = 'user_saved_products'
                      and column_name in (
                        'name','brand','category','tone','remote','match_score','price_from','merchant_count',
                        'satisfies','misses','note','pros','cons','review_score','review_count',
                        'review_insight','offers','provides'
                      )
                      and is_nullable = 'NO'
                    """, String.class, schema);
            assertThat(nonNullablePayloadColumns).isEmpty();
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void migrationAllowsPresentationSnapshotsButRejectsCommercialSnapshots() throws Exception {
        String schema = "pcos010_guard_" + UUID.randomUUID().toString().replace("-", "");
        UUID savedId = UUID.randomUUID();
        jdbcTemplate.execute("create schema " + schema);
        try {
            createLegacyTables(schema);
            runMigration(schema);
            insertIdentifierOnlySavedProduct(schema, savedId);

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from %s.user_saved_products where id = ?".formatted(schema),
                    Integer.class,
                    savedId
            )).isEqualTo(1);
            insertWithLegacyPayload(schema, UUID.randomUUID());
            jdbcTemplate.update(
                    "update %s.user_saved_products set image_url = ?, note = ? where id = ?".formatted(schema),
                    "https://saved.test/image.jpg",
                    "Saved by the user",
                    savedId
            );

            for (Map.Entry<String, Object> payload : prohibitedPayloadValues()) {
                assertThatThrownBy(() -> jdbcTemplate.update(
                        "update %s.user_saved_products set %s = ? where id = ?"
                                .formatted(schema, payload.getKey()),
                        payload.getValue(),
                        savedId
                )).as(payload.getKey())
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_user_saved_products_no_commercial_snapshot");
            }
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    private void createLegacyTables(String schema) {
        jdbcTemplate.execute("""
                create table %s.user_product_searches (
                    id uuid primary key
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.user_product_search_results (
                    id uuid primary key
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.user_saved_products (
                    id uuid primary key,
                    user_id uuid not null,
                    product_key text not null,
                    product_hash text,
                    name text not null,
                    brand text not null,
                    category text not null,
                    tone text not null,
                    image_url text,
                    product_url text,
                    remote boolean not null,
                    match_score integer not null,
                    price_from double precision not null,
                    merchant_count integer not null,
                    satisfies text not null,
                    misses text not null,
                    note text not null,
                    pros text not null,
                    cons text not null,
                    review_score double precision not null,
                    review_count integer not null,
                    review_insight text not null,
                    offers text not null,
                    needs text,
                    provides text not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """.formatted(schema));
    }

    private void insertLegacySavedProduct(String schema, UUID id) {
        Instant now = Instant.parse("2026-07-01T00:00:00Z");
        jdbcTemplate.update("""
                insert into %s.user_saved_products (
                    id, user_id, product_key, product_hash, name, brand, category, tone,
                    image_url, product_url, remote, match_score, price_from, merchant_count,
                    satisfies, misses, note, pros, cons, review_score, review_count, review_insight,
                    offers, needs, provides, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(schema),
                id,
                UUID.randomUUID(),
                "legacy-product",
                "provider-hash",
                "Provider title",
                "Provider brand",
                "Provider category",
                "#fff",
                "https://provider.test/image.jpg",
                "https://provider.test/product",
                true,
                99,
                0.0d,
                2,
                "[\"filter\"]",
                "[]",
                "Generated note",
                "[\"pro\"]",
                "[\"con\"]",
                5.0d,
                100,
                "Review payload",
                "[{\"price\":0}]",
                "later-ticket",
                "[\"port\"]",
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private void insertIdentifierOnlySavedProduct(String schema, UUID id) {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        jdbcTemplate.update("""
                insert into %s.user_saved_products (
                    id, user_id, product_key, source_provider, source_type, source_identity,
                    external_product_id, selected_options_json, retention_policy_key,
                    reference_verified_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(schema),
                id,
                UUID.randomUUID(),
                "identifier-only",
                "GENERIC_UCP",
                "MERCHANT_STOREFRONT",
                "MEANT_MERCHANT_SEMANTIC",
                "product-1",
                "[]",
                "generic-ucp-storefront-v2",
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private void insertWithLegacyPayload(String schema, UUID id) {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        jdbcTemplate.update("""
                insert into %s.user_saved_products (
                    id, user_id, product_key, name, source_provider, source_type, source_identity,
                    external_product_id, selected_options_json, retention_policy_key,
                    reference_verified_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(schema),
                id,
                UUID.randomUUID(),
                "forbidden-insert",
                "Forbidden provider title",
                "GENERIC_UCP",
                "MERCHANT_STOREFRONT",
                "MEANT_MERCHANT_SEMANTIC",
                "product-1",
                "[]",
                "generic-ucp-storefront-v2",
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private List<Map.Entry<String, Object>> prohibitedPayloadValues() {
        return List.of(
                Map.entry("price_from", 10.5d),
                Map.entry("offers", "[]")
        );
    }

    private void runMigration(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             AutoCloseable ignored = selectSchema(connection, schema)) {
            SingleConnectionDataSource schemaDataSource = new SingleConnectionDataSource(connection, true);
            runChangeLog(schemaDataSource, schema,
                    "classpath:db/changelog/migration/030-add-catalog-retention-policy.xml");
            runChangeLog(schemaDataSource, schema,
                    "classpath:db/changelog/migration/038-restore-saved-product-presentation.xml");
        }
    }

    private void runChangeLog(SingleConnectionDataSource dataSource, String schema, String changeLog)
            throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setDefaultSchema(schema);
        liquibase.setLiquibaseSchema(schema);
        liquibase.afterPropertiesSet();
    }

    private AutoCloseable selectSchema(Connection connection, String schema) throws SQLException {
        String current = connection.getSchema();
        connection.setSchema(schema);
        return () -> connection.setSchema(current == null || current.isBlank() ? "public" : current);
    }
}
