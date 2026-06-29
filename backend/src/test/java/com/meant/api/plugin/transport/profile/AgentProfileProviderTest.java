package com.meant.api.plugin.transport.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.URI;
import java.util.List;
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
        assertThatThrownBy(() -> firstProfile.capabilities().add(
                capability("dev.ucp.shopping.checkout", "create_checkout", false).advertisements().getFirst()
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> firstProfile.supportedVersions().put("2027-01-01", "https://ucp.dev/2027-01-01"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generatedProfileSerializesToUcpAgentProfileShape() throws Exception {
        AgentProfile profile = new CapabilityRegistry(List.of(
                capability("dev.ucp.shopping.catalog.search", "search_catalog", true)
        )).agentProfile(identity());
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(profile));

        assertThat(root.path("profile_url").asText())
                .isEqualTo("https://agent.example/.well-known/ucp-agent.json");
        assertThat(root.path("protocol_version").asText()).isEqualTo(PROTOCOL_VERSION);
        assertThat(root.path("supported_versions").path(PROTOCOL_VERSION).asText())
                .isEqualTo("https://ucp.dev/" + PROTOCOL_VERSION);
        assertThat(root.path("signing_key_id").asText()).isEqualTo("agent-key-1");
        assertThat(root.path("capabilities").isArray()).isTrue();
        assertThat(root.path("capabilities").get(0).path("id").asText())
                .isEqualTo("dev.ucp.shopping.catalog.search");
        assertThat(root.path("capabilities").get(0).path("tools").get(0).asText())
                .isEqualTo("search_catalog");
        assertThat(root.path("capabilities").get(0).path("required").asBoolean()).isTrue();
        assertThat(root.path("capabilities").get(0).path("protocol_versions").path("min").asText())
                .isEqualTo(PROTOCOL_VERSION);
        assertThat(root.path("capabilities").get(0).path("protocol_versions").path("max").asText())
                .isEqualTo(PROTOCOL_VERSION);
        assertThat(root.path("capabilities").get(0).path("requires").path("required_capabilities").isArray())
                .isTrue();
        assertThat(root.path("capabilities").get(0).path("requires").path("optional_capabilities").isArray())
                .isTrue();
    }

    private static AgentIdentity identity() {
        return new AgentIdentity(
                URI.create("https://agent.example/.well-known/ucp-agent.json"),
                PROTOCOL_VERSION,
                "agent-key-1"
        );
    }

    private static TestCapability capability(String id, String toolName, boolean required) {
        CapabilityId capabilityId = CapabilityId.of(id);
        CapabilityAdvertisement advertisement = new CapabilityAdvertisement(
                capabilityId,
                "1.0.0",
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
