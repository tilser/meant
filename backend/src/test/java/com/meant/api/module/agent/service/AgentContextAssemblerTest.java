package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentModelRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.dto.AgentModelContext;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentContextAssemblerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SEARCH_MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID CART_MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID OLD_CART_MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID TRIGGER_MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final String ROUTING_SCOPE = "SHOPIFY:merchant-jackets";
    private static final Instant BASE = Instant.parse("2026-07-19T10:00:00Z");

    private final AgentConversationRepository conversations = mock(AgentConversationRepository.class);
    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentMessageRepository messages = mock(AgentMessageRepository.class);
    private final AgentArtifactReferenceRepository artifacts = mock(AgentArtifactReferenceRepository.class);
    private final ShoppingMissionRepository missions = mock(ShoppingMissionRepository.class);
    private final AgentContextAssembler assembler = new AgentContextAssembler(
            conversations,
            runs,
            messages,
            artifacts,
            missions,
            properties(),
            new ObjectMapper()
    );

    @Test
    void laterRunReceivesOrderedTurnsNumberedProductsAndOnlyTheCurrentCartLines() {
        UUID currentCartId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID currentLineId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID removedLineId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        CartLine currentLine = new CartLine(currentLineId, "offer-jacket-2", "Black cotton jacket");
        CartLine removedLine = new CartLine(removedLineId, "offer-jacket-1", "Canvas field jacket");
        givenRunAndMessages();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(any(), any()))
                .thenReturn(List.of(
                        cart(CART_MESSAGE_ID, 1, currentCartId, BASE.plusSeconds(3), List.of(currentLine)),
                        cartLine(CART_MESSAGE_ID, 2, currentCartId, BASE.plusSeconds(3), currentLine),
                        cart(OLD_CART_MESSAGE_ID, 1, currentCartId, BASE.plusSeconds(2),
                                List.of(currentLine, removedLine)),
                        cartLine(OLD_CART_MESSAGE_ID, 2, currentCartId, BASE.plusSeconds(2), currentLine),
                        cartLine(OLD_CART_MESSAGE_ID, 3, currentCartId, BASE.plusSeconds(2), removedLine),
                        product(SEARCH_MESSAGE_ID, 1, "product-jacket-1", "offer-jacket-1",
                                "Canvas field jacket"),
                        offer(SEARCH_MESSAGE_ID, 1, "product-jacket-1", "offer-jacket-1"),
                        product(SEARCH_MESSAGE_ID, 2, "product-jacket-2", "offer-jacket-2",
                                "Black cotton jacket"),
                        offer(SEARCH_MESSAGE_ID, 2, "product-jacket-2", "offer-jacket-2")
                ));

        AgentModelContext context = assembler.assemble(RUN_ID);

        assertThat(context.triggeringUserText()).isEqualTo("Please remove it from the cart.");
        assertThat(context.messages()).extracting(AgentModelMessage::role)
                .containsSubsequence(
                        AgentModelRole.USER,
                        AgentModelRole.ASSISTANT,
                        AgentModelRole.USER,
                        AgentModelRole.ASSISTANT,
                        AgentModelRole.USER
                );
        assertThat(context.messages()).extracting(AgentModelMessage::text)
                .endsWith(
                        "Find me two jackets.",
                        "I found two jackets.",
                        "Put the second jacket into the cart.",
                        "The black jacket is in your cart.",
                        "Please remove it from the cart."
                );

        String systemPrompt = context.messages().getFirst().text();
        assertThat(systemPrompt)
                .contains("Resolve ordinals against the newest compatible numbered product set.")
                .contains("Reuse an existing compatible cart with add_cart_line instead of prepare_carts.")
                .contains("most recently removed offer reference");

        String grounding = context.messages().get(1).text();
        assertThat(grounding)
                .contains("item=1 key=product-jacket-1 [PRODUCT] Canvas field jacket")
                .contains("item=2 key=product-jacket-2 [PRODUCT] Black cotton jacket")
                .contains("cartId=" + currentCartId)
                .contains("cartLineId=" + currentLineId + " offerKey=offer-jacket-2 label=Black cotton jacket")
                .doesNotContain("cartLineId=" + removedLineId)
                .contains("priorCartLineId=" + removedLineId + " offerKey=offer-jacket-1")
                .doesNotContain("[CART_LINE]");
    }

    @Test
    void lowerOrdinalEmptySnapshotWinsForTheSameMessageAndTimestamp() {
        UUID currentCartId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID staleCartId = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID staleLineId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        CartLine staleLine = new CartLine(staleLineId, "offer-stale", "Removed jacket");
        givenRunAndMessages();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(any(), any()))
                .thenReturn(List.of(
                        cart(CART_MESSAGE_ID, 1, currentCartId, BASE.plusSeconds(3), List.of()),
                        cart(CART_MESSAGE_ID, 2, staleCartId, BASE.plusSeconds(3),
                                "shopify:merchant-jackets", List.of(staleLine)),
                        cartLine(CART_MESSAGE_ID, 3, staleCartId, BASE.plusSeconds(3), staleLine)
                ));

        String grounding = assembler.assemble(RUN_ID).messages().get(1).text();

        assertThat(grounding)
                .contains("cartId=" + currentCartId + " routingScopeKey=" + ROUTING_SCOPE)
                .contains("label=Cart at jackets.example lines=none")
                .doesNotContain("cartId=" + staleCartId)
                .doesNotContain("cartLineId=" + staleLineId)
                .doesNotContain("priorCartLineId=" + staleLineId);
    }

    @Test
    void fallbackIdentityUsesMerchantBeforeExternalIdentityWhenRoutingScopeIsMissing() {
        UUID currentCartId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID staleCartId = UUID.fromString("00000000-0000-0000-0000-000000000402");
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000403");
        UUID staleLineId = UUID.fromString("00000000-0000-0000-0000-000000000404");
        CartLine staleLine = new CartLine(staleLineId, "offer-stale", "Stale jacket");
        givenRunAndMessages();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(any(), any()))
                .thenReturn(List.of(
                        fallbackCart(
                                CART_MESSAGE_ID,
                                currentCartId,
                                BASE.plusSeconds(3),
                                merchantId,
                                "external-current",
                                List.of()
                        ),
                        fallbackCart(
                                OLD_CART_MESSAGE_ID,
                                staleCartId,
                                BASE.plusSeconds(2),
                                merchantId,
                                "external-stale",
                                List.of(staleLine)
                        ),
                        cartLine(OLD_CART_MESSAGE_ID, 2, staleCartId, BASE.plusSeconds(2), staleLine)
                ));

        String grounding = assembler.assemble(RUN_ID).messages().get(1).text();

        assertThat(grounding)
                .contains("cartId=" + currentCartId + " routingScopeKey=cart:" + currentCartId)
                .contains("lines=none")
                .doesNotContain("cartId=" + staleCartId)
                .doesNotContain("cartLineId=" + staleLineId);
    }

    @Test
    void newestCartSnapshotWinsBeforeItsIdentityChangesToAuthoritativeRouting() {
        UUID cartId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID staleLineId = UUID.fromString("00000000-0000-0000-0000-000000000503");
        UUID postRemovalMessageId = UUID.fromString("00000000-0000-0000-0000-000000000504");
        CartLine staleLine = new CartLine(staleLineId, "offer-stale", "Removed jacket");
        givenRunAndMessages();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(any(), any()))
                .thenReturn(List.of(
                        cart(CART_MESSAGE_ID, 1, cartId, BASE.plusSeconds(4), List.of()),
                        fallbackCart(
                                postRemovalMessageId,
                                cartId,
                                BASE.plusSeconds(3),
                                merchantId,
                                "external-stale",
                                List.of()
                        ),
                        fallbackCart(
                                OLD_CART_MESSAGE_ID,
                                cartId,
                                BASE.plusSeconds(2),
                                merchantId,
                                "external-stale",
                                List.of(staleLine)
                        ),
                        cartLine(OLD_CART_MESSAGE_ID, 2, cartId, BASE.plusSeconds(2), staleLine)
                ));

        String grounding = assembler.assemble(RUN_ID).messages().get(1).text();

        assertThat(grounding)
                .contains("cartId=" + cartId + " routingScopeKey=" + ROUTING_SCOPE)
                .contains("lines=none")
                .doesNotContain("cartLineId=" + staleLineId)
                .contains("priorCartLineId=" + staleLineId)
                .containsOnlyOnce("- cartId=" + cartId);
    }

    private void givenRunAndMessages() {
        AgentConversation conversation = AgentConversation.builder()
                .id(CONVERSATION_ID)
                .userId(USER_ID)
                .title("Jackets")
                .status(AgentConversationStatus.ACTIVE)
                .createdAt(BASE)
                .updatedAt(BASE)
                .build();
        AgentRun run = AgentRun.builder()
                .id(RUN_ID)
                .conversationId(CONVERSATION_ID)
                .userId(USER_ID)
                .triggeringMessageId(TRIGGER_MESSAGE_ID)
                .status(AgentRunStatus.RUNNING)
                .model("model")
                .promptVersion("v1")
                .createdAt(BASE)
                .build();
        List<AgentMessage> chronological = List.of(
                message(1, AgentMessageRole.USER, "Find me two jackets."),
                message(2, AgentMessageRole.ASSISTANT, "I found two jackets."),
                message(3, AgentMessageRole.USER, "Put the second jacket into the cart."),
                message(4, AgentMessageRole.ASSISTANT, "The black jacket is in your cart."),
                AgentMessage.builder()
                        .id(TRIGGER_MESSAGE_ID)
                        .conversationId(CONVERSATION_ID)
                        .role(AgentMessageRole.USER)
                        .contentKind(AgentContentKind.TEXT)
                        .sequenceNumber(5)
                        .textContent("Please remove it from the cart.")
                        .createdAt(BASE.plusSeconds(5))
                        .build()
        );
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(conversations.findByIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdOrderBySequenceNumberDesc(any(), any()))
                .thenReturn(chronological.reversed());
    }

    private AgentMessage message(long sequence, AgentMessageRole role, String text) {
        return AgentMessage.builder()
                .conversationId(CONVERSATION_ID)
                .role(role)
                .contentKind(AgentContentKind.TEXT)
                .sequenceNumber(sequence)
                .textContent(text)
                .createdAt(BASE.plusSeconds(sequence))
                .build();
    }

    private AgentArtifactReference product(
            UUID messageId,
            int ordinal,
            String productKey,
            String offerKey,
            String label
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.PRODUCT)
                .ordinal(ordinal)
                .stableKey(productKey)
                .label(label)
                .canonicalProductKey(productKey)
                .offerKey(offerKey)
                .payloadJson("{}")
                .createdAt(BASE)
                .build();
    }

    private AgentArtifactReference offer(
            UUID messageId,
            int ordinal,
            String productKey,
            String offerKey
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.OFFER)
                .ordinal(ordinal)
                .stableKey(offerKey)
                .canonicalProductKey(productKey)
                .offerKey(offerKey)
                .payloadJson("{}")
                .createdAt(BASE)
                .build();
    }

    private AgentArtifactReference cart(
            UUID messageId,
            int ordinal,
            UUID cartId,
            Instant createdAt,
            List<CartLine> lines
    ) {
        return cart(messageId, ordinal, cartId, createdAt, ROUTING_SCOPE, lines);
    }

    private AgentArtifactReference cart(
            UUID messageId,
            int ordinal,
            UUID cartId,
            Instant createdAt,
            String routingScopeKey,
            List<CartLine> lines
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.CART)
                .ordinal(ordinal)
                .stableKey("cart:" + cartId)
                .label("Cart at jackets.example")
                .cartId(cartId)
                .payloadJson("""
                        {"cartId":"%s","routingScopeKey":"%s","lines":[%s]}
                        """.formatted(cartId, routingScopeKey, String.join(",", lines.stream()
                        .map(this::cartLineJson)
                        .toList())).strip())
                .createdAt(createdAt)
                .build();
    }

    private AgentArtifactReference cartLine(
            UUID messageId,
            int ordinal,
            UUID cartId,
            Instant createdAt,
            CartLine line
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.CART_LINE)
                .ordinal(ordinal)
                .stableKey("cart-line:" + line.id())
                .label(line.title())
                .offerKey(line.offerKey())
                .cartId(cartId)
                .cartLineId(line.id())
                .payloadJson(cartLineJson(line))
                .createdAt(createdAt)
                .build();
    }

    private AgentArtifactReference fallbackCart(
            UUID messageId,
            UUID cartId,
            Instant createdAt,
            UUID merchantId,
            String externalMerchantId,
            List<CartLine> lines
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .label("Cart at jackets.example")
                .cartId(cartId)
                .payloadJson("""
                        {"cartId":"%s","provider":"SHOPIFY","merchantId":"%s",\
                        "externalMerchantId":"%s","lines":[%s]}
                        """.formatted(
                        cartId,
                        merchantId,
                        externalMerchantId,
                        String.join(",", lines.stream().map(this::cartLineJson).toList())
                ).strip())
                .createdAt(createdAt)
                .build();
    }

    private String cartLineJson(CartLine line) {
        return """
                {"cartLineId":"%s","offerKey":"%s","productTitle":"%s"}
                """.formatted(line.id(), line.offerKey(), line.title()).strip();
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 256, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }

    private record CartLine(UUID id, String offerKey, String title) {
    }
}
