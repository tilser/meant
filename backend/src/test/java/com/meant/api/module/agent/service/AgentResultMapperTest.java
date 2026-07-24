package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentRunEvent;
import com.meant.api.module.cart.service.BuyerSafeRoutingScopeKey;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentResultMapperTest {

    private static final String TECHNICAL_SCOPE =
            "SHOPIFY:merchant:gid://shopify/Shop/1:domain:seller.myshopify.com";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sanitizesConversationTitlesAndRunSafeMessages() {
        Instant now = Instant.parse("2026-07-24T10:00:00Z");
        AgentConversation conversation = AgentConversation.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .title("History from seller.myshopify.com")
                .status(AgentConversationStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        AgentRun run = AgentRun.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.FAILED)
                .model("test")
                .promptVersion("test")
                .safeMessage("Retry mcp.shop.example.")
                .createdAt(now)
                .build();

        assertThat(AgentResultMapper.conversation(conversation).title())
                .isEqualTo("History from the merchant");
        assertThat(AgentResultMapper.run(run).safeMessage()).isEqualTo("Retry the merchant.");
    }

    @Test
    void sanitizesMessageTextAndEveryBuyerVisibleContentString() throws Exception {
        AgentMessage message = AgentMessage.builder()
                .id(UUID.randomUUID())
                .runId(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .sequenceNumber(7)
                .role(AgentMessageRole.ASSISTANT)
                .contentKind(AgentContentKind.TEXT)
                .textContent("Continue with seller.myshopify.com, then view "
                        + "https://official.example/products/shoe.")
                .contentJson("""
                        {
                          "summary": "Ask api.mcp.shop.example for help.",
                          "nested": ["Retry https://transport.example/api/ucp/mcp/session/1"],
                          "officialUrl": "https://official.example/products/shoe"
                        }
                        """)
                .createdAt(Instant.parse("2026-07-24T10:00:00Z"))
                .build();

        var result = AgentResultMapper.message(message);
        JsonNode content = objectMapper.readTree(result.contentJson());

        assertThat(result.textContent()).isEqualTo(
                "Continue with the merchant, then view https://official.example/products/shoe.");
        assertThat(content.path("summary").asText()).isEqualTo("Ask the merchant for help.");
        assertThat(content.path("nested").get(0).asText()).isEqualTo("Retry the merchant");
        assertThat(content.path("officialUrl").asText())
                .isEqualTo("https://official.example/products/shoe");
    }

    @Test
    void sanitizesNestedReplayEventPayloadBeforeItLeavesTheServiceBoundary() throws Exception {
        AgentRunEvent event = AgentRunEvent.builder()
                .id(UUID.randomUUID())
                .runId(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .cursor(13)
                .eventType(AgentRunEventType.ASSISTANT_DELTA)
                .schemaVersion(1)
                .payloadJson("""
                        {
                          "text": "Still checking seller.myshopify.com.",
                          "tool": {
                            "summary": "Called https://transport.example/mcp?session=1.",
                            "officialUrl": "https://official.example/products/shoe"
                          }
                        }
                        """)
                .occurredAt(Instant.parse("2026-07-24T10:00:00Z"))
                .build();

        var result = AgentResultMapper.event(event);
        JsonNode payload = objectMapper.readTree(result.payloadJson());

        assertThat(payload.path("text").asText()).isEqualTo("Still checking the merchant.");
        assertThat(payload.path("tool").path("summary").asText())
                .isEqualTo("Called the merchant.");
        assertThat(payload.path("tool").path("officialUrl").asText())
                .isEqualTo("https://official.example/products/shoe");
    }

    @Test
    void sanitizesArtifactPayloadAndRemovesTransportCoordinates() throws Exception {
        AgentArtifactReference artifact = AgentArtifactReference.builder()
                .id(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .runId(UUID.randomUUID())
                .artifactType(AgentArtifactType.CART)
                .ordinal(0)
                .stableKey("cart-1")
                .label("Cart from seller.myshopify.com")
                .payloadJson("""
                        {
                          "routingScopeKey": "SHOPIFY:merchant:gid://shopify/Shop/1:domain:seller.myshopify.com",
                          "endpoint": "https://mcp.shop.example",
                          "lines": [{
                            "summary": "Sold by seller.myshopify.com",
                            "uri": "https://transport.example/mcp",
                            "officialUrl": "https://official.example/products/shoe"
                          }]
                        }
                        """)
                .createdAt(Instant.parse("2026-07-24T10:00:00Z"))
                .build();

        var result = AgentResultMapper.artifact(artifact);
        JsonNode payload = objectMapper.readTree(result.payloadJson());

        assertThat(payload.path("routingScopeKey").asText())
                .isEqualTo(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE));
        assertThat(result.label()).isEqualTo("Cart from the merchant");
        assertThat(payload.has("endpoint")).isFalse();
        assertThat(payload.path("lines").get(0).has("uri")).isFalse();
        assertThat(payload.path("lines").get(0).path("summary").asText())
                .isEqualTo("Sold by the merchant");
        assertThat(payload.path("lines").get(0).path("officialUrl").asText())
                .isEqualTo("https://official.example/products/shoe");
    }
}
