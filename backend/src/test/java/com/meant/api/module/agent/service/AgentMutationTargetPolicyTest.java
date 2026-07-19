package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentMutationTargetPolicyTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private final AgentArtifactReferenceRepository artifacts = mock(AgentArtifactReferenceRepository.class);
    private final AgentMutationTargetPolicy policy = new AgentMutationTargetPolicy(artifacts, new ObjectMapper());

    @Test
    void secondProductOnlyAcceptsAnOfferFromTheSecondProductInTheLatestResultSet() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference first = product(messageId, 1, "product-1", "offer-1");
        AgentArtifactReference second = product(messageId, 2, "product-2", "offer-2");
        AgentArtifactReference secondOffer = offer(messageId, 2, "product-2", "offer-2");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(first, second, secondOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-2"))
                .thenReturn(Optional.of(secondOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-1"))
                .thenReturn(Optional.of(offer(messageId, 1, "product-1", "offer-1")));

        assertThat(policy.matchesExplicitOrdinal(
                context("Add the second one."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-2\"}]}"
        )).isTrue();
        assertThat(policy.matchesExplicitOrdinal(
                context("Add the second one."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-1\"}]}"
        )).isFalse();
        assertThat(policy.matchesExplicitOrdinal(
                context("Add the second one."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-2\"},{\"offerKey\":\"offer-1\"}]}"
        )).isFalse();
    }

    @Test
    void ordinalPinMustUseTheCanonicalProductFromThatPosition() {
        UUID messageId = UUID.randomUUID();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        product(messageId, 1, "product-1", "offer-1"),
                        product(messageId, 2, "product-2", "offer-2")
                ));

        assertThat(policy.matchesMutationTarget(
                context("Pin the first one."),
                "pin_product",
                "{\"canonicalProductKey\":\"product-1\"}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Pin the first one."),
                "pin_product",
                "{\"canonicalProductKey\":\"product-2\"}"
        )).isFalse();
    }

    @Test
    void nonOrdinalProductDescriptionCannotSelectAnArbitraryIssuedProduct() {
        assertThat(policy.matchesMutationTarget(
                context("Pin the gray pair."),
                "pin_product",
                "{\"canonicalProductKey\":\"product-2\"}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Watch those shoes."),
                "watch_product",
                "{\"canonicalProductKey\":\"product-1\"}"
        )).isFalse();
    }

    @Test
    void uniqueProductDescriptionBindsTheExactIssuedOfferWithoutTrustingTheModelLabel() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference first = productWithLabel(
                messageId, 1, "product-1", "offer-1", "Blue trail runners");
        AgentArtifactReference second = productWithLabel(
                messageId, 2, "product-2", "offer-2", "Gray city sneakers");
        AgentArtifactReference secondOffer = offer(messageId, 2, "product-2", "offer-2");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(first, second, secondOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-2"))
                .thenReturn(Optional.of(secondOffer));

        assertThat(policy.matchesMutationTarget(
                context("Add the gray pair to my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-2\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Add the gray pair to my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-1\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Add the gray pair to my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-2\"},{\"offerKey\":\"offer-2\"}]}"
        )).isFalse();
    }

    @Test
    void ambiguousProductDescriptionCannotAuthorizeEitherIssuedOffer() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference first = productWithLabel(
                messageId, 1, "product-1", "offer-1", "Gray trail runners");
        AgentArtifactReference second = productWithLabel(
                messageId, 2, "product-2", "offer-2", "Gray city sneakers");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(first, second));

        assertThat(policy.matchesMutationTarget(
                context("Add the gray pair to my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-2\"}]}"
        )).isFalse();
    }

    @Test
    void cartLineMutationRequiresTheExactDisplayedLineOrdinal() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID firstLineId = UUID.randomUUID();
        UUID secondLineId = UUID.randomUUID();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartLine(messageId, 2, cartId, firstLineId),
                        cartLine(messageId, 3, cartId, secondLineId)
                ));

        String secondArguments = "{\"cartId\":\"" + cartId + "\",\"cartLineId\":\""
                + secondLineId + "\",\"quantity\":3}";
        assertThat(policy.matchesMutationTarget(
                context("Remove the gray pair from my cart."), "remove_cart_line", secondArguments
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Remove the second line."), "remove_cart_line", secondArguments
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Set the second line quantity to 3."), "update_cart_line", secondArguments
        )).isTrue();

        String wrongArguments = "{\"cartId\":\"" + cartId + "\",\"cartLineId\":\""
                + firstLineId + "\"}";
        assertThat(policy.matchesMutationTarget(
                context("Remove the second line."), "remove_cart_line", wrongArguments
        )).isFalse();
    }

    @Test
    void delegatedMissionCanOnlyMutateItsSelectedOffersAndAttachedCarts() {
        UUID missionCartId = UUID.randomUUID();
        UUID unrelatedCartId = UUID.randomUUID();
        ShoppingMission mission = ShoppingMission.builder()
                .status(ShoppingMissionStatus.READY)
                .coverageJson("[{\"selections\":[{\"offerKey\":\"offer-1\"}]}]")
                .cartReferencesJson("[\"" + missionCartId + "\"]")
                .checkoutReferencesJson("[]")
                .build();

        assertThat(policy.matchesDelegatedMission(
                mission, "prepare_carts", "{\"offers\":[{\"offerKey\":\"offer-1\"}]}"
        )).isTrue();
        assertThat(policy.matchesDelegatedMission(
                mission, "prepare_carts", "{\"offers\":[{\"offerKey\":\"offer-2\"}]}"
        )).isFalse();
        assertThat(policy.matchesDelegatedMission(
                mission, "prepare_checkout", "{\"cartIds\":[\"" + missionCartId + "\"]}"
        )).isTrue();
        assertThat(policy.matchesDelegatedMission(
                mission, "prepare_checkout", "{\"cartIds\":[\"" + unrelatedCartId + "\"]}"
        )).isFalse();
    }

    @Test
    void proceedBindsCheckoutPreparationToTheLatestCartSetAndRejectsAStaleCart() {
        UUID messageId = UUID.randomUUID();
        UUID latestCartId = UUID.randomUUID();
        UUID staleCartId = UUID.randomUUID();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(cart(messageId, 1, latestCartId)));

        assertThat(policy.matchesMutationTarget(
                context("Proceed to checkout."),
                "prepare_checkout",
                "{\"cartIds\":[\"" + latestCartId + "\"]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Proceed to checkout."),
                "prepare_checkout",
                "{\"cartIds\":[\"" + staleCartId + "\"]}"
        )).isFalse();
    }

    @Test
    void checkoutUpdateRequiresOneLatestCheckoutArtifact() {
        UUID messageId = UUID.randomUUID();
        UUID latestCartId = UUID.randomUUID();
        UUID otherCartId = UUID.randomUUID();
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(checkout(messageId, 1, latestCartId)));

        assertThat(policy.matchesMutationTarget(
                context("Use this shipping address for checkout."),
                "update_checkout",
                "{\"cartId\":\"" + latestCartId + "\"}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Use this shipping address for checkout."),
                "update_checkout",
                "{\"cartId\":\"" + otherCartId + "\"}"
        )).isFalse();

        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        checkout(messageId, 1, latestCartId),
                        checkout(messageId, 2, otherCartId)
                ));
        assertThat(policy.matchesMutationTarget(
                context("Use this shipping address for checkout."),
                "update_checkout",
                "{\"cartId\":\"" + latestCartId + "\"}"
        )).isFalse();
    }

    @Test
    void firstTwoBindsAComparisonAndCartPreparationToExactlyThoseProducts() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference first = product(messageId, 1, "product-1", "offer-1");
        AgentArtifactReference second = product(messageId, 2, "product-2", "offer-2");
        AgentArtifactReference third = product(messageId, 3, "product-3", "offer-3");
        AgentArtifactReference firstOffer = offer(messageId, 1, "product-1", "offer-1");
        AgentArtifactReference secondOffer = offer(messageId, 2, "product-2", "offer-2");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(first, second, third, firstOffer, secondOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-1"))
                .thenReturn(Optional.of(firstOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-2"))
                .thenReturn(Optional.of(secondOffer));

        assertThat(policy.matchesExplicitOrdinal(
                context("Compare the first two."),
                "compare_products",
                "{\"canonicalProductKeys\":[\"product-1\",\"product-2\"]}"
        )).isTrue();
        assertThat(policy.matchesExplicitOrdinal(
                context("Compare the first two."),
                "compare_products",
                "{\"canonicalProductKeys\":[\"product-1\",\"product-3\"]}"
        )).isFalse();
        assertThat(policy.matchesExplicitOrdinal(
                context("Buy the first two."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-1\"},{\"offerKey\":\"offer-2\"}]}"
        )).isTrue();
    }

    @Test
    void aDirectClickDoesNotDependOnNaturalLanguageOrdinalResolution() {
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), CONVERSATION_ID, null, UUID.randomUUID(), "Clicked Add to cart");

        assertThat(policy.matchesMutationTarget(context, "prepare_carts", "{\"offers\":[]}"))
                .isTrue();
    }

    private AgentToolExecutionContext context(String text) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), CONVERSATION_ID, UUID.randomUUID(), UUID.randomUUID(), text);
    }

    private AgentArtifactReference product(
            UUID messageId,
            int ordinal,
            String productKey,
            String offerKey
    ) {
        return reference(messageId, AgentArtifactType.PRODUCT, ordinal, productKey, productKey, offerKey);
    }

    private AgentArtifactReference productWithLabel(
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
                .createdAt(Instant.parse("2026-07-18T12:00:00Z"))
                .build();
    }

    private AgentArtifactReference offer(
            UUID messageId,
            int ordinal,
            String productKey,
            String offerKey
    ) {
        return reference(messageId, AgentArtifactType.OFFER, ordinal, offerKey, productKey, offerKey);
    }

    private AgentArtifactReference cartLine(
            UUID messageId,
            int ordinal,
            UUID cartId,
            UUID cartLineId
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.CART_LINE)
                .ordinal(ordinal)
                .stableKey("cart-line:" + cartLineId)
                .cartId(cartId)
                .cartLineId(cartLineId)
                .payloadJson("{}")
                .createdAt(Instant.parse("2026-07-18T12:00:00Z"))
                .build();
    }

    private AgentArtifactReference cart(UUID messageId, int ordinal, UUID cartId) {
        return commerceArtifact(messageId, AgentArtifactType.CART, ordinal, "cart:" + cartId, cartId);
    }

    private AgentArtifactReference checkout(UUID messageId, int ordinal, UUID cartId) {
        return commerceArtifact(messageId, AgentArtifactType.CHECKOUT, ordinal, "checkout-cart:" + cartId, cartId);
    }

    private AgentArtifactReference commerceArtifact(
            UUID messageId,
            AgentArtifactType type,
            int ordinal,
            String stableKey,
            UUID cartId
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(type)
                .ordinal(ordinal)
                .stableKey(stableKey)
                .cartId(cartId)
                .payloadJson("{}")
                .createdAt(Instant.parse("2026-07-18T12:00:00Z"))
                .build();
    }

    private AgentArtifactReference reference(
            UUID messageId,
            AgentArtifactType type,
            int ordinal,
            String stableKey,
            String productKey,
            String offerKey
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(type)
                .ordinal(ordinal)
                .stableKey(stableKey)
                .canonicalProductKey(productKey)
                .offerKey(offerKey)
                .payloadJson("{}")
                .createdAt(Instant.parse("2026-07-18T12:00:00Z"))
                .build();
    }
}
