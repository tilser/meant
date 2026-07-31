package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReferenceIntegrityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    private final AgentConversationRepository conversations = mock(AgentConversationRepository.class);
    private final AgentArtifactReferenceRepository artifacts = mock(AgentArtifactReferenceRepository.class);
    private final ReferenceIntegrityPolicy policy = new ReferenceIntegrityPolicy(
            conversations,
            artifacts,
            new AgentCartSnapshotSupport(new ObjectMapper()),
            new ObjectMapper()
    );

    @Test
    void acceptsOnlyServerIssuedReferencesIncludingNestedArguments() {
        AgentToolExecutionContext context = context();
        UUID inventoryItemId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        own(context);
        when(artifacts.findByConversationIdOrderByCreatedAtAscOrdinalAsc(context.conversationId()))
                .thenReturn(List.of(
                        reference(context, AgentArtifactType.PRODUCT, "product-1", "product-1", "offer-1", null),
                        reference(context, AgentArtifactType.OFFER, "offer-1", "product-1", "offer-1", null),
                        reference(context, AgentArtifactType.INVENTORY_ITEM,
                                "inventory:" + inventoryItemId, null, null, inventoryItemId),
                        reference(context, AgentArtifactType.MISSION,
                                "mission:" + missionId, null, null, null),
                        cart(context, cartId, cartLineId, NOW)
                ));

        var validation = policy.validate(context, "prepare_carts", """
                {
                  "offers":[{"offerKey":"offer-1"}],
                  "canonicalProductKey":"product-1",
                  "inventoryItemId":"%s",
                  "missionId":"%s",
                  "cartId":"%s",
                  "cartLineId":"%s"
                }
                """.formatted(inventoryItemId, missionId, cartId, cartLineId));

        assertThat(validation).isEqualTo(ReferenceIntegrityPolicy.Validation.ok());
    }

    @Test
    void rejectsAHallucinatedOfferWithStructuredReferenceError() {
        AgentToolExecutionContext context = context();
        own(context);
        when(artifacts.findByConversationIdOrderByCreatedAtAscOrdinalAsc(context.conversationId()))
                .thenReturn(List.of());

        var validation = policy.validate(
                context,
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"invented-offer\"}]}"
        );

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.code()).isEqualTo("reference_not_found");
        assertThat(validation.field()).isEqualTo("offerKey");
    }

    @Test
    void rejectsCartLinesFromAHistoricalSnapshotAsStale() {
        AgentToolExecutionContext context = context();
        UUID cartId = UUID.randomUUID();
        UUID staleLineId = UUID.randomUUID();
        UUID currentLineId = UUID.randomUUID();
        own(context);
        when(artifacts.findByConversationIdOrderByCreatedAtAscOrdinalAsc(context.conversationId()))
                .thenReturn(List.of(
                        cart(context, cartId, staleLineId, NOW.minusSeconds(60)),
                        cart(context, cartId, currentLineId, NOW)
                ));

        var validation = policy.validate(
                context,
                "remove_cart_line",
                "{\"cartId\":\"%s\",\"cartLineId\":\"%s\"}"
                        .formatted(cartId, staleLineId)
        );

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.code()).isEqualTo("stale_reference");
        assertThat(validation.field()).isEqualTo("cartLineId");
    }

    @Test
    void rejectsACartLinePairedWithTheWrongCurrentCart() {
        AgentToolExecutionContext context = context();
        UUID firstCart = UUID.randomUUID();
        UUID firstLine = UUID.randomUUID();
        UUID secondCart = UUID.randomUUID();
        UUID secondLine = UUID.randomUUID();
        own(context);
        when(artifacts.findByConversationIdOrderByCreatedAtAscOrdinalAsc(context.conversationId()))
                .thenReturn(List.of(
                        cart(context, firstCart, firstLine, "SHOPIFY:first", NOW),
                        cart(context, secondCart, secondLine, "SHOPIFY:second", NOW)
                ));

        var validation = policy.validate(
                context,
                "remove_cart_line",
                "{\"cartId\":\"%s\",\"cartLineId\":\"%s\"}"
                        .formatted(firstCart, secondLine)
        );

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.code()).isEqualTo("reference_relationship_mismatch");
        assertThat(validation.field()).isEqualTo("cartLineId");
    }

    @Test
    void rejectsConversationOutsideTheAuthenticatedUsersScopeBeforeInspectingArtifacts() {
        AgentToolExecutionContext context = context();
        when(conversations.findByIdAndUserId(context.conversationId(), context.userId()))
                .thenReturn(Optional.empty());

        var validation = policy.validate(context, "search_catalog", "{\"query\":\"shoes\"}");

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.code()).isEqualTo("reference_ownership_mismatch");
        assertThat(validation.field()).isEqualTo("conversationId");
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "request");
    }

    private void own(AgentToolExecutionContext context) {
        when(conversations.findByIdAndUserId(context.conversationId(), context.userId()))
                .thenReturn(Optional.of(mock(AgentConversation.class)));
    }

    private AgentArtifactReference reference(
            AgentToolExecutionContext context,
            AgentArtifactType type,
            String stableKey,
            String productKey,
            String offerKey,
            UUID inventoryItemId
    ) {
        return AgentArtifactReference.builder()
                .conversationId(context.conversationId())
                .messageId(UUID.randomUUID())
                .artifactType(type)
                .ordinal(1)
                .stableKey(stableKey)
                .canonicalProductKey(productKey)
                .offerKey(offerKey)
                .inventoryItemId(inventoryItemId)
                .payloadJson("{}")
                .createdAt(NOW.minusSeconds(120))
                .build();
    }

    private AgentArtifactReference cart(
            AgentToolExecutionContext context,
            UUID cartId,
            UUID cartLineId,
            Instant createdAt
    ) {
        return cart(context, cartId, cartLineId, "SHOPIFY:merchant", createdAt);
    }

    private AgentArtifactReference cart(
            AgentToolExecutionContext context,
            UUID cartId,
            UUID cartLineId,
            String routingScope,
            Instant createdAt
    ) {
        return AgentArtifactReference.builder()
                .conversationId(context.conversationId())
                .messageId(UUID.randomUUID())
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .cartId(cartId)
                .payloadJson("""
                        {"routingScopeKey":"%s","lines":[
                          {"cartLineId":"%s","offerKey":"offer-1","label":"Cart item"}
                        ]}
                        """.formatted(routingScope, cartLineId))
                .createdAt(createdAt)
                .build();
    }
}
