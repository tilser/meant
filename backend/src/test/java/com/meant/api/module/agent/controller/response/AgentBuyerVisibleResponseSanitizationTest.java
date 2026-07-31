package com.meant.api.module.agent.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.service.dto.AgentArtifactResult;
import com.meant.api.module.agent.service.dto.AgentConversationResult;
import com.meant.api.module.agent.service.dto.AgentConversationSummaryResult;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import com.meant.api.module.agent.service.dto.AgentRunResult;
import com.meant.api.module.agent.service.dto.AgentUserActionResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentBuyerVisibleResponseSanitizationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-24T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void messageResponseSanitizesResultsThatDidNotPassThroughTheEntityMapper() throws Exception {
        AgentMessageResult result = new AgentMessageResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                AgentMessageRole.ASSISTANT,
                AgentContentKind.TEXT,
                "I need details before continuing with seller.myshopify.com.",
                """
                        {
                          "text": "Retry mcp.shop.example.",
                          "officialUrl": "https://official.example/products/shoe"
                        }
                        """,
                null,
                OCCURRED_AT
        );

        AgentMessageResponse response = AgentMessageResponse.from(result);
        JsonNode content = objectMapper.readTree(response.contentJson());

        assertThat(response.textContent())
                .isEqualTo("I need details before continuing with the merchant.");
        assertThat(content.path("text").asText()).isEqualTo("Retry the merchant.");
        assertThat(content.path("officialUrl").asText())
                .isEqualTo("https://official.example/products/shoe");
    }

    @Test
    void messageResponseStripsLinksWhoseDestinationsAreTransportCoordinates() {
        AgentMessageResult result = new AgentMessageResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                AgentMessageRole.ASSISTANT,
                AgentContentKind.TEXT,
                "Order at [the store](https://seller.myshopify.com/products/x), then see "
                        + "[the size guide](https://official.example/size-guide).",
                null,
                null,
                OCCURRED_AT
        );

        AgentMessageResponse response = AgentMessageResponse.from(result);

        assertThat(response.textContent())
                .isEqualTo("Order at the store, then see "
                        + "[the size guide](https://official.example/size-guide).")
                .doesNotContain("myshopify.com", "[the store](the merchant)");
    }

    @Test
    void eventResponseSanitizesReplayPayloadsThatDidNotPassThroughTheEntityMapper()
            throws Exception {
        AgentRunEventResult result = new AgentRunEventResult(
                1,
                9,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tool.completed",
                OCCURRED_AT,
                """
                        {
                          "summary": "Called [the store](https://transport.example/api/ucp/mcp/session/2).",
                          "result": {
                            "message": "seller.myshopify.com replied.",
                            "officialUrl": "https://official.example/products/shoe"
                          }
                        }
                        """
        );

        AgentRunEventResponse response = AgentRunEventResponse.from(result);
        JsonNode payload = objectMapper.readTree(response.payloadJson());

        assertThat(payload.path("summary").asText()).isEqualTo("Called the store.");
        assertThat(payload.path("result").path("message").asText())
                .isEqualTo("the merchant replied.");
        assertThat(payload.path("result").path("officialUrl").asText())
                .isEqualTo("https://official.example/products/shoe");
    }

    @Test
    void everyDirectAgentResponseSanitizesFreeFormTextAndPayloads() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AgentMessageResult message = new AgentMessageResult(
                messageId,
                null,
                1,
                AgentMessageRole.USER_ACTION,
                AgentContentKind.ACTION,
                "Added by seller.myshopify.com.",
                "{}",
                null,
                OCCURRED_AT
        );
        AgentArtifactResult artifact = new AgentArtifactResult(
                UUID.randomUUID(),
                messageId,
                null,
                AgentArtifactType.CART,
                0,
                "cart-1",
                "Cart at seller.myshopify.com",
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                """
                        {
                          "endpoint":"https://mcp.shop.example",
                          "message":"seller.myshopify.com replied"
                        }
                        """,
                OCCURRED_AT
        );

        AgentConversationResponse conversation = AgentConversationResponse.from(
                new AgentConversationResult(
                        conversationId,
                        "History from seller.myshopify.com",
                        AgentConversationStatus.ACTIVE,
                        "Waiting for mcp.shop.example.",
                        1,
                        null,
                        null,
                        1,
                        null,
                        0,
                        List.of(message),
                        List.of(artifact),
                        OCCURRED_AT,
                        OCCURRED_AT
                )
        );
        AgentConversationSummaryResponse summary = AgentConversationSummaryResponse.from(
                new AgentConversationSummaryResult(
                        conversationId,
                        "History from seller.myshopify.com",
                        AgentConversationStatus.ACTIVE,
                        null,
                        null,
                        1,
                        OCCURRED_AT,
                        OCCURRED_AT
                )
        );
        AgentRunResponse run = AgentRunResponse.from(new AgentRunResult(
                UUID.randomUUID(),
                conversationId,
                AgentRunStatus.FAILED,
                "model",
                "v1",
                1,
                1,
                null,
                null,
                "failed",
                "Retry mcp.shop.example.",
                false,
                0,
                OCCURRED_AT,
                OCCURRED_AT,
                OCCURRED_AT
        ));
        AgentUserActionResponse action = AgentUserActionResponse.from(new AgentUserActionResult(
                message,
                """
                        {
                          "endpoint":"https://mcp.shop.example",
                          "message":"seller.myshopify.com replied"
                        }
                        """,
                List.of(artifact)
        ));

        assertThat(conversation.title()).isEqualTo("History from the merchant");
        assertThat(conversation.rollingSummary()).isEqualTo("Waiting for the merchant.");
        assertThat(conversation.artifacts().getFirst().label()).isEqualTo("Cart at the merchant");
        assertThat(conversation.artifacts().getFirst().payloadJson())
                .doesNotContain("endpoint", "myshopify.com", "mcp.shop.example");
        assertThat(summary.title()).isEqualTo("History from the merchant");
        assertThat(run.safeMessage()).isEqualTo("Retry the merchant.");
        assertThat(action.resultJson())
                .doesNotContain("endpoint", "myshopify.com", "mcp.shop.example");
        assertThat(objectMapper.readTree(action.resultJson()).path("message").asText())
                .isEqualTo("the merchant replied");
    }
}
