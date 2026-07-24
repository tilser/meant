package com.meant.api.module.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.SQLException;
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
class CartMerchantDomainMigrationIT extends PostgresIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void backfillsCanonicalStorefrontAndRetainsTechnicalRoutingAuthorityAndArtifacts() throws Exception {
        String schema = "cart_origin_" + UUID.randomUUID().toString().replace("-", "");
        UUID merchantId = UUID.randomUUID();
        UUID shopifyIntegrationId = UUID.randomUUID();
        UUID genericIntegrationId = UUID.randomUUID();
        UUID externalCartId = UUID.randomUUID();
        UUID unverifiedExternalCartId = UUID.randomUUID();
        UUID managedShopifyCartId = UUID.randomUUID();
        UUID managedGenericCartId = UUID.randomUUID();
        String storefront = "allbirds.com";
        String routingDomain = "weareallbirds.myshopify.com";
        String unverifiedRoutingDomain = "unverified-shop.myshopify.com";
        String genericRoutingDomain = "mcp.allbirds.example";
        jdbcTemplate.execute("create schema " + schema);
        try {
            createLegacyTables(schema);
            insertMerchantIdentity(schema, merchantId, storefront);
            insertLegacyCarts(
                    schema,
                    merchantId,
                    shopifyIntegrationId,
                    genericIntegrationId,
                    externalCartId,
                    unverifiedExternalCartId,
                    managedShopifyCartId,
                    managedGenericCartId,
                    storefront,
                    routingDomain,
                    unverifiedRoutingDomain,
                    genericRoutingDomain
            );

            runMigration(schema);
            runMigration(schema);

            Map<String, Object> externalCart = jdbcTemplate.queryForMap("""
                    select merchant_domain, routing_domain
                    from %s.cart
                    where id = ?
                    """.formatted(schema), externalCartId);
            Map<String, Object> managedShopifyCart = jdbcTemplate.queryForMap("""
                    select merchant_domain, routing_domain
                    from %s.cart
                    where id = ?
                    """.formatted(schema), managedShopifyCartId);
            Map<String, Object> unverifiedExternalCart = jdbcTemplate.queryForMap("""
                    select merchant_domain, routing_domain
                    from %s.cart
                    where id = ?
                    """.formatted(schema), unverifiedExternalCartId);
            Map<String, Object> managedGenericCart = jdbcTemplate.queryForMap("""
                    select merchant_domain, routing_domain
                    from %s.cart
                    where id = ?
                    """.formatted(schema), managedGenericCartId);
            Map<String, Object> artifact = jdbcTemplate.queryForMap("""
                    select label, payload_json
                    from %s.agent_artifact_reference
                    where cart_id = ?
                    """.formatted(schema), externalCartId);
            Map<String, Object> unverifiedArtifact = jdbcTemplate.queryForMap("""
                    select label, payload_json
                    from %s.agent_artifact_reference
                    where cart_id = ?
                    """.formatted(schema), unverifiedExternalCartId);

            assertThat(externalCart)
                    .containsEntry("merchant_domain", storefront)
                    .containsEntry("routing_domain", routingDomain);
            assertThat(managedShopifyCart)
                    .containsEntry("merchant_domain", storefront)
                    .containsEntry("routing_domain", routingDomain);
            assertThat(unverifiedExternalCart)
                    .containsEntry("merchant_domain", null)
                    .containsEntry("routing_domain", unverifiedRoutingDomain);
            assertThat(managedGenericCart)
                    .containsEntry("merchant_domain", storefront)
                    .containsEntry("routing_domain", genericRoutingDomain);
            assertThat(artifact)
                    .containsEntry("label", "Cart at " + storefront);
            assertThat((String) artifact.get("payload_json"))
                    .contains("\"merchantOrigin\": \"allbirds.com\"")
                    .doesNotContain("\"merchantDomain\"")
                    .doesNotContain(routingDomain);
            assertThat(unverifiedArtifact)
                    .containsEntry("label", "Cart");
            assertThat((String) unverifiedArtifact.get("payload_json"))
                    .doesNotContain("\"merchantDomain\"")
                    .doesNotContain("\"merchantOrigin\"")
                    .doesNotContain(unverifiedRoutingDomain);
        } finally {
            jdbcTemplate.execute("drop schema " + schema + " cascade");
        }
    }

    private void createLegacyTables(String schema) {
        jdbcTemplate.execute("""
                create table %s.merchant (
                    id uuid primary key,
                    domain text not null
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.merchant_identity (
                    merchant_id uuid not null,
                    namespace text not null,
                    role text not null,
                    normalized_value text not null
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.merchant_integration (
                    id uuid primary key,
                    merchant_id uuid not null,
                    provider text not null,
                    verified_domain text,
                    endpoint text
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.cart (
                    id uuid primary key,
                    merchant_id uuid,
                    merchant_domain text,
                    provider text,
                    merchant_integration_id uuid,
                    external_merchant_id text,
                    routing_scope_key text,
                    checkout_attempt_id uuid,
                    constraint ck_cart_legacy_or_bound_scope check (
                        (
                            provider is null and routing_scope_key is null
                            and merchant_integration_id is null and external_merchant_id is null
                            and merchant_id is not null and merchant_domain is not null
                        ) or (
                            provider is not null and routing_scope_key is not null
                            and merchant_integration_id is not null
                            and merchant_id is not null and merchant_domain is not null
                        ) or (
                            provider is not null and routing_scope_key is not null
                            and merchant_integration_id is null and merchant_id is null
                            and external_merchant_id is not null and merchant_domain is not null
                        )
                    )
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.user_inventory_items (
                    id uuid primary key,
                    source_checkout_attempt_id uuid,
                    provider text,
                    merchant_integration_id uuid,
                    external_merchant_id text,
                    external_merchant_domain text
                )
                """.formatted(schema));
        jdbcTemplate.execute("""
                create table %s.agent_artifact_reference (
                    artifact_type text not null,
                    cart_id uuid,
                    label text,
                    payload_json text not null
                )
                """.formatted(schema));
    }

    private void insertMerchantIdentity(String schema, UUID merchantId, String storefront) {
        jdbcTemplate.update(
                "insert into %s.merchant (id, domain) values (?, ?)".formatted(schema),
                merchantId,
                storefront
        );
        jdbcTemplate.update("""
                insert into %s.merchant_identity (
                    merchant_id, namespace, role, normalized_value
                ) values (?, 'SHOPIFY_SHOP', 'PROVIDER_ID', 'gid://shopify/shop/1')
                """.formatted(schema), merchantId);
    }

    private void insertLegacyCarts(
            String schema,
            UUID merchantId,
            UUID shopifyIntegrationId,
            UUID genericIntegrationId,
            UUID externalCartId,
            UUID unverifiedExternalCartId,
            UUID managedShopifyCartId,
            UUID managedGenericCartId,
            String storefront,
            String routingDomain,
            String unverifiedRoutingDomain,
            String genericRoutingDomain
    ) {
        String endpoint = "https://" + routingDomain + "/api/ucp/mcp";
        jdbcTemplate.update("""
                insert into %s.merchant_integration (
                    id, merchant_id, provider, verified_domain, endpoint
                ) values (?, ?, 'SHOPIFY', ?, ?)
                """.formatted(schema), shopifyIntegrationId, merchantId, storefront, endpoint);
        jdbcTemplate.update("""
                insert into %s.merchant_integration (
                    id, merchant_id, provider, verified_domain, endpoint
                ) values (?, ?, 'GENERIC_UCP', ?, ?)
                """.formatted(schema),
                genericIntegrationId,
                merchantId,
                storefront,
                "https://" + genericRoutingDomain + "/api/ucp/mcp"
        );
        jdbcTemplate.update("""
                insert into %s.cart (
                    id, merchant_id, merchant_domain, provider, merchant_integration_id,
                    external_merchant_id, routing_scope_key
                ) values (?, null, ?, 'SHOPIFY', null, 'gid://shopify/Shop/1', ?)
                """.formatted(schema),
                externalCartId,
                routingDomain,
                "SHOPIFY:merchant:gid://shopify/Shop/1:domain:" + routingDomain
        );
        jdbcTemplate.update("""
                insert into %s.cart (
                    id, merchant_id, merchant_domain, provider, merchant_integration_id,
                    external_merchant_id, routing_scope_key
                ) values (?, null, ?, 'SHOPIFY', null, 'gid://shopify/Shop/2', ?)
                """.formatted(schema),
                unverifiedExternalCartId,
                unverifiedRoutingDomain,
                "SHOPIFY:merchant:gid://shopify/Shop/2:domain:" + unverifiedRoutingDomain
        );
        jdbcTemplate.update("""
                insert into %s.cart (
                    id, merchant_id, merchant_domain, provider, merchant_integration_id,
                    external_merchant_id, routing_scope_key
                ) values (?, ?, 'allbirds.com', 'SHOPIFY', ?, 'gid://shopify/Shop/1', ?)
                """.formatted(schema),
                managedShopifyCartId,
                merchantId,
                shopifyIntegrationId,
                "SHOPIFY:integration:" + shopifyIntegrationId
        );
        jdbcTemplate.update("""
                insert into %s.cart (
                    id, merchant_id, merchant_domain, provider, merchant_integration_id,
                    external_merchant_id, routing_scope_key
                ) values (?, ?, 'allbirds.com', 'GENERIC_UCP', ?, null, ?)
                """.formatted(schema),
                managedGenericCartId,
                merchantId,
                genericIntegrationId,
                "GENERIC_UCP:integration:" + genericIntegrationId
        );
        jdbcTemplate.update("""
                insert into %s.agent_artifact_reference (
                    artifact_type, cart_id, label, payload_json
                ) values ('CART', ?, ?, ?)
                """.formatted(schema),
                externalCartId,
                "Cart at " + routingDomain,
                "{\"merchantDomain\":\"" + routingDomain + "\",\"cartId\":\"" + externalCartId + "\"}"
        );
        jdbcTemplate.update("""
                insert into %s.agent_artifact_reference (
                    artifact_type, cart_id, label, payload_json
                ) values ('CART', ?, ?, ?)
                """.formatted(schema),
                unverifiedExternalCartId,
                "Cart at " + unverifiedRoutingDomain,
                "{\"merchantDomain\":\"" + unverifiedRoutingDomain + "\",\"cartId\":\""
                        + unverifiedExternalCartId + "\"}"
        );
    }

    private void runMigration(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
             AutoCloseable ignored = selectSchema(connection, schema)) {
            SingleConnectionDataSource schemaDataSource = new SingleConnectionDataSource(connection, true);
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(schemaDataSource);
            liquibase.setChangeLog(
                    "classpath:db/changelog/migration/058-separate-cart-merchant-domain-routing-authority.xml");
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
