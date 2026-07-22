package com.meant.api.plugin.transport.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.catalog.extension.CatalogExtensionRegistry;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyGlobalCatalogExtensionCapability;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyGlobalCatalogExtensionProperties;
import com.meant.api.plugin.catalog.getproduct.CatalogGetProductCapability;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.signing.JsonWebKey;
import com.meant.api.plugin.signing.PublicSigningKey;
import com.meant.api.plugin.signing.SigningKeyProvider;
import com.meant.api.plugin.signing.SigningKeyPurpose;
import com.meant.api.plugin.signing.SigningKeyStatus;
import com.meant.api.plugin.signing.SigningKey;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentProfileProviderTest {

    private static final String PROTOCOL_VERSION = "2026-04-08";

    @Test
    void generatesProfileOnceAndServesSameImmutableInstance() {
        CountingCapabilityRegistry registry = new CountingCapabilityRegistry(List.of(
                capability("dev.ucp.shopping.catalog.search", "search_catalog", true)
        ));

        AgentProfileProvider provider = new AgentProfileProvider(registry, identity());
        AgentProfile firstProfile = provider.profile();
        AgentProfile secondProfile = provider.profile();

        assertThat(firstProfile).isSameAs(secondProfile);
        assertThat(registry.generationCount()).isEqualTo(1);
        assertThatThrownBy(() -> firstProfile.ucp().capabilities().put("dev.ucp.shopping.checkout", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> firstProfile.ucp().services().get("dev.ucp.shopping").add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generatedProfileMatchesUcp20260408PlatformContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CatalogExtensionRegistry extensionRegistry = new CatalogExtensionRegistry(List.of());
        AgentProfileProvider provider = new AgentProfileProvider(new CapabilityRegistry(List.of(
                new CatalogSearchCapability(objectMapper, extensionRegistry),
                new CatalogLookupCapability(objectMapper, extensionRegistry),
                new CatalogGetProductCapability(objectMapper, extensionRegistry),
                new ShopifyGlobalCatalogExtensionCapability(
                        new ShopifyGlobalCatalogExtensionProperties(PROTOCOL_VERSION)
                )
        )), identity(), new StaticSigningKeyProvider(List.of(publicSigningKey())));
        AgentProfile profile = provider.profile();

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(profile));

        JsonNode ucp = root.path("ucp");
        assertThat(ucp.path("version").asText()).isEqualTo(PROTOCOL_VERSION);
        assertThat(ucp.path("services").path("dev.ucp.shopping").get(0).path("version").asText())
                .isEqualTo(PROTOCOL_VERSION);
        assertThat(ucp.path("services").path("dev.ucp.shopping").get(0).path("transport").asText())
                .isEqualTo("mcp");
        assertThat(ucp.path("services").path("dev.ucp.shopping").get(0).path("schema").asText())
                .isEqualTo("https://ucp.dev/2026-04-08/services/shopping/mcp.openrpc.json");
        assertThat(ucp.path("payment_handlers").isObject()).isTrue();
        assertThat(ucp.path("payment_handlers").size()).isZero();

        JsonNode capabilities = ucp.path("capabilities");
        assertThat(capabilities.properties().stream().map(Map.Entry::getKey).toList())
                .containsExactly(
                        "dev.shopify.catalog.global",
                        "dev.ucp.shopping.catalog.lookup",
                        "dev.ucp.shopping.catalog.search"
                );
        assertThat(capabilities.path("dev.ucp.shopping.catalog.lookup").size()).isEqualTo(1);
        assertThat(capabilities.path("dev.ucp.shopping.catalog.lookup").get(0).path("version").asText())
                .isEqualTo(PROTOCOL_VERSION);
        assertThat(capabilities.path("dev.ucp.shopping.catalog.search").get(0).path("version").asText())
                .isEqualTo(PROTOCOL_VERSION);
        assertThat(capabilities.path("dev.shopify.catalog.global").get(0).path("extends").values().stream()
                .map(JsonNode::asText).toList())
                .containsExactly(
                        "dev.ucp.shopping.catalog.search",
                        "dev.ucp.shopping.catalog.lookup"
                );
        assertThat(capabilities.has("dev.ucp.shopping.catalog.get_product")).isFalse();

        assertThat(root.path("keys").isArray()).isTrue();
        assertThat(root.path("keys").get(0).path("kid").asText()).isEqualTo("agent-key-1");
        assertThat(root.path("keys").get(0).path("kty").asText()).isEqualTo("EC");
        assertThat(root.path("keys").get(0).path("d").isMissingNode()).isTrue();
        assertThat(root.has("profile_url")).isFalse();
        assertThat(root.has("protocol_version")).isFalse();
        assertThat(root.has("supported_versions")).isFalse();
        assertThat(root.has("signing_key_id")).isFalse();
        assertThat(root.has("signing_keys")).isFalse();
    }

    private static AgentIdentity identity() {
        return new AgentIdentity(
                URI.create("https://agent.example/.well-known/ucp-agent.json"),
                PROTOCOL_VERSION,
                "agent-key-1"
        );
    }

    private static PublicSigningKey publicSigningKey() {
        return new PublicSigningKey(
                "agent-key-1",
                SigningKeyPurpose.TRANSPORT,
                SigningKeyStatus.ACTIVE,
                new JsonWebKey(
                        "EC",
                        "agent-key-1",
                        "P-256",
                        "qIVYZVLCrPZHGHjP17CTW0_-D9Lfw0EkjqF7xB4FivA",
                        "Mc4nN9LTDOBhfoUeg8Ye9WedFRhnZXZJA12Qp0zZ6F0",
                        null,
                        null,
                        "ES256",
                        null,
                        List.of()
                ),
                null
        );
    }

    private static TestCapability capability(String id, String toolName, boolean required) {
        CapabilityId capabilityId = CapabilityId.of(id);
        CapabilityAdvertisement advertisement = new CapabilityAdvertisement(
                capabilityId,
                PROTOCOL_VERSION,
                List.of(toolName),
                required,
                CapabilityAdvertisement.ProtocolVersions.exact(PROTOCOL_VERSION),
                CapabilityAdvertisement.Requirements.none(),
                URI.create("https://ucp.dev/spec/" + id),
                URI.create("https://ucp.dev/schema/" + id + ".json")
        );
        return new TestCapability(capabilityId, List.of(advertisement));
    }

    private static class CountingCapabilityRegistry extends CapabilityRegistry {

        private int generationCount;

        CountingCapabilityRegistry(List<UcpCapability<?, ?>> capabilities) {
            super(capabilities);
        }

        @Override
        public AgentProfile agentProfile(AgentIdentity identity) {
            generationCount++;
            return super.agentProfile(identity);
        }

        int generationCount() {
            return generationCount;
        }
    }

    private record StaticSigningKeyProvider(List<PublicSigningKey> publicKeys) implements SigningKeyProvider {

        @Override
        public SigningKey activePrivateKey(SigningKeyPurpose purpose) {
            throw new UnsupportedOperationException("No private keys in this test provider");
        }

        @Override
        public Optional<SigningKey> key(String kid, SigningKeyPurpose purpose) {
            return Optional.empty();
        }
    }

    private record TestCapability(
            CapabilityId id,
            List<CapabilityAdvertisement> advertisements
    ) implements UcpCapability<Void, String> {

        @Override
        public Object buildArguments(Void request, NegotiatedCapabilities activeCapabilities) {
            return null;
        }

        @Override
        public String parseResponse(UcpToolResponse response) {
            return response.textContent();
        }
    }
}
