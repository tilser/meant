package com.meant.api.plugin.transport.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.meant.api.plugin.transport.profile.AgentProfile;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {

    private static final String PROTOCOL_VERSION = "2026-04-08";

    @Test
    void looksUpCapabilityByToolName() {
        TestCapability catalogSearch = capability(
                "dev.ucp.shopping.catalog.search",
                "search_catalog",
                true
        );
        CapabilityRegistry registry = new CapabilityRegistry(List.of(catalogSearch));

        assertThat(registry.capabilityForTool("search_catalog")).isSameAs(catalogSearch);
        assertThat(registry.capabilityForTool(" search_catalog ")).isSameAs(catalogSearch);
    }

    @Test
    void unknownToolThrows() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(
                capability("dev.ucp.shopping.catalog.search", "search_catalog", true)
        ));

        assertThatThrownBy(() -> registry.capabilityForTool("get_product"))
                .isInstanceOf(UnknownUcpToolException.class)
                .hasMessageContaining("get_product");
    }

    @Test
    void duplicateToolAdvertisementsFailFast() {
        TestCapability catalogSearch = capability(
                "dev.ucp.shopping.catalog.search",
                "search_catalog",
                true
        );
        TestCapability alternateSearch = capability(
                "dev.ucp.shopping.catalog.search.v2",
                "search_catalog",
                false
        );

        assertThatThrownBy(() -> new CapabilityRegistry(List.of(catalogSearch, alternateSearch)))
                .isInstanceOf(DuplicateUcpToolException.class)
                .hasMessageContaining("search_catalog");
    }

    @Test
    void generatesProfileFromAllCapabilities() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(
                capability("dev.ucp.shopping.checkout", "create_checkout", false),
                capability("dev.ucp.shopping.catalog.search", "search_catalog", true)
        ));

        AgentProfile profile = registry.agentProfile(identity());

        assertThat(profile.profileUrl()).isEqualTo(URI.create("https://agent.example/.well-known/ucp-agent.json"));
        assertThat(profile.protocolVersion()).isEqualTo(PROTOCOL_VERSION);
        assertThat(profile.supportedVersions())
                .containsExactlyEntriesOf(java.util.Map.of(PROTOCOL_VERSION, "https://ucp.dev/" + PROTOCOL_VERSION));
        assertThat(profile.signingKeyId()).isEqualTo("agent-key-1");
        assertThat(profile.capabilities())
                .extracting(advertisement -> advertisement.id().value())
                .containsExactly("dev.ucp.shopping.catalog.search", "dev.ucp.shopping.checkout");
        assertThat(profile.capabilities())
                .extracting(CapabilityAdvertisement::required)
                .containsExactly(true, false);
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
