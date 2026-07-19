package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentMutationTargetPolicyTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private final AgentArtifactReferenceRepository artifacts = mock(AgentArtifactReferenceRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentMutationTargetPolicy policy = new AgentMutationTargetPolicy(
            artifacts,
            objectMapper,
            new AgentCartSnapshotSupport(objectMapper)
    );

    @Test
    void missionMutationMustUseTheSelectedMissionId() {
        ShoppingMission mission = mock(ShoppingMission.class);
        UUID missionId = UUID.randomUUID();
        when(mission.getId()).thenReturn(missionId);

        assertThat(policy.matchesMissionTarget(
                mission, "{\"missionId\":\"" + missionId + "\"}"
        )).isTrue();
        assertThat(policy.matchesMissionTarget(
                mission, "{\"missionId\":\"" + UUID.randomUUID() + "\"}"
        )).isFalse();
        assertThat(policy.matchesMissionTarget(mission, "{}")).isFalse();
    }

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
    void conversationalLeadInDoesNotOverrideTheExplicitThirdOrdinal() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference first = productWithLabel(
                messageId, 1, "product-1", "offer-1", "Duck camo trucker hat");
        AgentArtifactReference second = productWithLabel(
                messageId, 2, "product-2", "offer-2", "Coastland camo trucker hat");
        AgentArtifactReference third = productWithLabel(
                messageId, 3, "product-3", "offer-3", "Mesh printed five panel hat");
        AgentArtifactReference fourth = productWithLabel(
                messageId, 4, "product-4", "offer-4", "Embroidered trucker hat");
        AgentArtifactReference firstOffer = offer(messageId, 1, "product-1", "offer-1");
        AgentArtifactReference secondOffer = offer(messageId, 2, "product-2", "offer-2");
        AgentArtifactReference thirdOffer = offer(messageId, 3, "product-3", "offer-3");
        AgentArtifactReference fourthOffer = offer(messageId, 4, "product-4", "offer-4");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(first, firstOffer, second, secondOffer, third, thirdOffer, fourth, fourthOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-1"))
                .thenReturn(Optional.of(firstOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-2"))
                .thenReturn(Optional.of(secondOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-3"))
                .thenReturn(Optional.of(thirdOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-4"))
                .thenReturn(Optional.of(fourthOffer));

        String turn = "ok looks good, add the third one into cart";
        assertThat(policy.matchesMutationTarget(
                context(turn),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-3\"}]}"
        )).isTrue();
        for (String wrongOffer : List.of("offer-1", "offer-2", "offer-4")) {
            assertThat(policy.matchesMutationTarget(
                    context(turn),
                    "prepare_carts",
                    "{\"offers\":[{\"offerKey\":\"" + wrongOffer + "\"}]}"
            )).isFalse();
        }
    }

    @Test
    void visibleThirdProductOverridesTheGlobalThirdProductOnALaterCarouselPage() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference globalThirdOffer = offer(messageId, 3, "product-3", "offer-3");
        AgentArtifactReference visibleThirdOffer = offer(messageId, 7, "product-7", "offer-7");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        product(messageId, 1, "product-1", "offer-1"),
                        product(messageId, 2, "product-2", "offer-2"),
                        product(messageId, 3, "product-3", "offer-3"),
                        product(messageId, 5, "product-5", "offer-5"),
                        product(messageId, 6, "product-6", "offer-6"),
                        product(messageId, 7, "product-7", "offer-7"),
                        product(messageId, 8, "product-8", "offer-8")
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-3"))
                .thenReturn(Optional.of(globalThirdOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(CONVERSATION_ID, "offer-7"))
                .thenReturn(Optional.of(visibleThirdOffer));
        AgentVisibleProductContext visible = new AgentVisibleProductContext(messageId, List.of(
                visibleProduct(1, 5),
                visibleProduct(2, 6),
                visibleProduct(3, 7),
                visibleProduct(4, 8)
        ));

        String turn = "ok looks good, add the third one into cart";
        assertThat(policy.matchesMutationTarget(
                context(turn, visible),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-7\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context(turn, visible),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-3\"}]}"
        )).isFalse();
    }

    @Test
    void unmatchedVisibleDescriptionCannotAuthorizeAnOrdinalMutation() {
        UUID messageId = UUID.randomUUID();
        AgentVisibleProductContext visible = new AgentVisibleProductContext(messageId, List.of(
                new AgentVisibleProductReference(1, 5, "product-5", "offer-5", "Green cap"),
                new AgentVisibleProductReference(2, 6, "product-6", "offer-6", "Brown cap"),
                new AgentVisibleProductReference(3, 7, "product-7", "offer-7", "Red wool cap"),
                new AgentVisibleProductReference(4, 8, "product-8", "offer-8", "Black cap")
        ));
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of());

        assertThat(policy.matchesMutationTarget(
                context("Add the third blue one to my cart.", visible),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-7\"}]}"
        )).isFalse();
    }

    @Test
    void conflictingVisibleOrdinalAndDescriptionRequiresAUserClarification() {
        UUID messageId = UUID.randomUUID();
        AgentVisibleProductContext visible = new AgentVisibleProductContext(messageId, List.of(
                new AgentVisibleProductReference(1, 5, "product-5", "offer-5", "Green cap"),
                new AgentVisibleProductReference(2, 6, "product-6", "offer-6", "Blue cap"),
                new AgentVisibleProductReference(3, 7, "product-7", "offer-7", "Red wool cap"),
                new AgentVisibleProductReference(4, 8, "product-8", "offer-8", "Black cap")
        ));
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of());

        assertThat(policy.requiresProductClarification(
                context("Add the third blue one to my cart.", visible),
                "prepare_carts"
        )).isTrue();
        assertThat(policy.requiresProductClarification(
                context("Add the third one to my cart.", visible),
                "prepare_carts"
        )).isFalse();
    }

    @Test
    void anAmbiguousNameRequiresClarificationButAUniqueVisibleNameDoesNot() {
        UUID messageId = UUID.randomUUID();
        AgentVisibleProductContext visible = new AgentVisibleProductContext(messageId, List.of(
                new AgentVisibleProductReference(1, 1, "product-1", "offer-1", "Blue cotton cap"),
                new AgentVisibleProductReference(2, 2, "product-2", "offer-2", "Blue wool cap"),
                new AgentVisibleProductReference(3, 3, "product-3", "offer-3", "Red trail hat")
        ));
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of());

        assertThat(policy.requiresProductClarification(
                context("Add the blue cap to my cart.", visible),
                "prepare_carts"
        )).isTrue();
        assertThat(policy.requiresProductClarification(
                context("Add the red trail hat to my cart.", visible),
                "prepare_carts"
        )).isFalse();
    }

    @Test
    void currentRunChoicesTakePrecedenceInAClarificationQuestion() {
        UUID currentMessageId = UUID.randomUUID();
        UUID visibleMessageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentArtifactReference currentFirst = withRunId(productWithLabel(
                currentMessageId, 1, "current-1", "current-offer-1", "Current blue cap"), runId);
        AgentArtifactReference currentSecond = withRunId(productWithLabel(
                currentMessageId, 2, "current-2", "current-offer-2", "Current red cap"), runId);
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(currentFirst, currentSecond));
        AgentVisibleProductContext visible = new AgentVisibleProductContext(visibleMessageId, List.of(
                new AgentVisibleProductReference(1, 5, "visible-5", "visible-offer-5", "Visible green cap"),
                new AgentVisibleProductReference(2, 6, "visible-6", "visible-offer-6", "Visible black cap")
        ));
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(),
                CONVERSATION_ID,
                runId,
                UUID.randomUUID(),
                "Add a cap to my cart.",
                null,
                null,
                null,
                visible
        );

        assertThat(policy.productClarificationCandidates(context, "prepare_carts"))
                .extracting(AgentVisibleProductReference::canonicalProductKey)
                .containsExactly("current-1", "current-2");
    }

    @Test
    void anExactClarificationReplyDoesNotAskAgainButAConflictingReplyDoes() {
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of());

        assertThat(policy.requiresProductClarification(
                context("2", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.requiresProductClarification(
                context("the second green one", clarification()),
                "prepare_carts"
        )).isTrue();
    }

    @Test
    void cancellingOrChangingAClarificationDoesNotTrapTheUserInAReaskLoop() {
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of());

        assertThat(policy.requiresProductClarification(
                context("Never mind", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.requiresProductClarification(
                context("No.", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.requiresProductClarification(
                context("Neither", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.requiresProductClarification(
                context("Find running shoes instead", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.requiresProductClarification(
                context("Show me the brown cap", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.requiresProductClarification(
                context("blue", clarification()),
                "prepare_carts"
        )).isTrue();
        assertThat(policy.requiresProductClarification(
                context("Add a cap to my cart", clarification()),
                "prepare_carts"
        )).isTrue();
    }

    @Test
    void aBareNumberSelectsExactlyOnePersistedClarificationCandidate() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference brownOffer = offer(messageId, 6, "product-6", "offer-6");
        AgentArtifactReference redOffer = offer(messageId, 7, "product-7", "offer-7");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(brownOffer, redOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-6")).thenReturn(Optional.of(brownOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-7")).thenReturn(Optional.of(redOffer));
        AgentProductClarification pending = clarification();

        assertThat(policy.matchesMutationTarget(
                context("2. Brown cap", pending),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("2. Green cap", pending),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("2 and 3", pending),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("2 3", pending),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("2.", pending),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-7\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("2."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isFalse();
    }

    @Test
    void conflictingOrdinalAndDescriptionInAClarificationAnswerFailsClosed() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference brownOffer = offer(messageId, 6, "product-6", "offer-6");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(brownOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-6")).thenReturn(Optional.of(brownOffer));

        assertThat(policy.matchesMutationTarget(
                context("the second green one", clarification()),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isFalse();
    }

    @Test
    void namedProductReadsOnlyAcceptTheUniquelyIdentifiedProduct() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference red = productWithLabel(
                messageId, 1, "product-red", "offer-red", "Red trail hat");
        AgentArtifactReference blue = productWithLabel(
                messageId, 2, "product-blue", "offer-blue", "Blue mesh hat");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(red, blue));

        AgentToolExecutionContext context = context("Show reviews for the Red trail hat");

        assertThat(policy.matchesExplicitOrdinal(
                context,
                "get_product_reviews",
                "{\"canonicalProductKey\":\"product-red\"}"
        )).isTrue();
        assertThat(policy.matchesExplicitOrdinal(
                context,
                "get_product_reviews",
                "{\"canonicalProductKey\":\"product-blue\"}"
        )).isFalse();
    }

    @Test
    void onlySelectionAnswersMayReusePendingActionConsent() {
        assertThat(policy.isPendingProductSelectionAnswer(
                context("2. Brown cap", clarification()),
                "prepare_carts"
        )).isTrue();
        assertThat(policy.isPendingProductSelectionAnswer(
                context("Brown cap", clarification()),
                "add_cart_line"
        )).isTrue();
        assertThat(policy.isPendingProductSelectionAnswer(
                context("Show me the brown cap", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isPendingProductSelectionAnswer(
                context("Never mind", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isPendingProductSelectionAnswer(
                context("None", clarification()),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isPendingProductSelectionAnswer(
                context("Add the brown cap", clarification()),
                "prepare_carts"
        )).isFalse();
    }

    @Test
    void aProductSetIssuedByTheCurrentRunSupersedesTheSubmittedViewport() {
        UUID priorMessageId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentArtifactReference currentProduct = withRunId(
                productWithLabel(
                        currentMessageId,
                        3,
                        "product-current-3",
                        "offer-current-3",
                        "Cotton jacket"
                ),
                runId
        );
        AgentArtifactReference currentOffer = offer(
                currentMessageId, 3, "product-current-3", "offer-current-3");
        AgentArtifactReference priorOffer = offer(
                priorMessageId, 7, "product-7", "offer-7");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(currentProduct));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-current-3")).thenReturn(Optional.of(currentOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-7")).thenReturn(Optional.of(priorOffer));
        AgentVisibleProductContext visible = new AgentVisibleProductContext(priorMessageId, List.of(
                visibleProduct(1, 5),
                visibleProduct(2, 6),
                visibleProduct(3, 7),
                visibleProduct(4, 8)
        ));
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(),
                CONVERSATION_ID,
                runId,
                UUID.randomUUID(),
                "Add the third result.",
                null,
                null,
                null,
                visible
        );

        assertThat(policy.matchesMutationTarget(
                context,
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-current-3\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context,
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-7\"}]}"
        )).isFalse();
    }

    @Test
    void descriptiveOrdinalUsesTheNewestMatchingProductSetInsteadOfAnUnrelatedLaterSet() {
        UUID jacketMessageId = UUID.randomUUID();
        UUID shoeMessageId = UUID.randomUUID();
        Instant jacketAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant shoeAt = Instant.parse("2026-07-19T10:01:00Z");
        AgentArtifactReference secondJacket = offer(
                jacketMessageId, 2, "product-jacket-2", "offer-jacket-2");
        AgentArtifactReference secondShoe = offer(
                shoeMessageId, 2, "product-shoe-2", "offer-shoe-2");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        withCreatedAt(productWithLabel(
                                shoeMessageId, 1, "product-shoe-1", "offer-shoe-1", "Blue trail shoe"), shoeAt),
                        withCreatedAt(productWithLabel(
                                shoeMessageId, 2, "product-shoe-2", "offer-shoe-2", "Gray city shoe"), shoeAt),
                        withCreatedAt(productWithLabel(
                                jacketMessageId, 1, "product-jacket-1", "offer-jacket-1", "Canvas field jacket"),
                                jacketAt),
                        withCreatedAt(productWithLabel(
                                jacketMessageId, 2, "product-jacket-2", "offer-jacket-2", "Black cotton jacket"),
                                jacketAt)
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-2")).thenReturn(Optional.of(secondJacket));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-shoe-2")).thenReturn(Optional.of(secondShoe));

        assertThat(policy.matchesMutationTarget(
                context("Put the second jacket in my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-jacket-2\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Put the second jacket in my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-shoe-2\"}]}"
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
    void laterCheckoutInstructionDoesNotPolluteTheNamedProductTarget() {
        UUID messageId = UUID.randomUUID();
        AgentArtifactReference blackHat = productWithLabel(
                messageId, 1, "product-black-hat", "offer-black-hat", "Black SF baseball hat");
        AgentArtifactReference whiteHat = productWithLabel(
                messageId, 2, "product-white-hat", "offer-white-hat", "White SF baseball hat");
        AgentArtifactReference blackOffer = offer(
                messageId, 1, "product-black-hat", "offer-black-hat");
        AgentArtifactReference whiteOffer = offer(
                messageId, 2, "product-white-hat", "offer-white-hat");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(blackHat, whiteHat, blackOffer, whiteOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-black-hat")).thenReturn(Optional.of(blackOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-white-hat")).thenReturn(Optional.of(whiteOffer));

        assertThat(policy.matchesMutationTarget(
                context("find a black SF hat and put it into cart, prepare the checkout for me"),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-black-hat\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("find a black SF hat and put it into cart, prepare the checkout for me"),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-white-hat\"}]}"
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
    void ambiguousNewestMatchingSetCannotFallBackToAnOlderUniqueProduct() {
        UUID newestMessageId = UUID.randomUUID();
        UUID olderMessageId = UUID.randomUUID();
        Instant newestAt = Instant.parse("2026-07-19T10:01:00Z");
        Instant olderAt = Instant.parse("2026-07-19T10:00:00Z");
        AgentArtifactReference olderOffer = offer(
                olderMessageId, 1, "product-old", "offer-old");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        withCreatedAt(productWithLabel(
                                newestMessageId, 1, "product-new-1", "offer-new-1", "Gray field jacket"),
                                newestAt),
                        withCreatedAt(productWithLabel(
                                newestMessageId, 2, "product-new-2", "offer-new-2", "Gray city jacket"),
                                newestAt),
                        withCreatedAt(productWithLabel(
                                olderMessageId, 1, "product-old", "offer-old", "Gray winter jacket"),
                                olderAt),
                        withCreatedAt(olderOffer, olderAt)
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-old")).thenReturn(Optional.of(olderOffer));

        assertThat(policy.matchesMutationTarget(
                context("Add the gray jacket to my cart."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-old\"}]}"
        )).isFalse();
    }

    @Test
    void cartLineMutationUsesTheLatestCompleteSnapshotAndExactDisplayedReference() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID firstLineId = UUID.randomUUID();
        UUID secondLineId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-18T12:00:00Z");
        CartSnapshotLine first = new CartSnapshotLine(
                firstLineId, "product-1", "offer-1", "Blue trail runners");
        CartSnapshotLine second = new CartSnapshotLine(
                secondLineId, "product-2", "offer-2", "Gray city sneakers");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(messageId, cartId, createdAt, List.of(first, second)),
                        cartLineSnapshot(messageId, 2, cartId, createdAt, first),
                        cartLineSnapshot(messageId, 3, cartId, createdAt, second)
                ));

        String secondArguments = "{\"cartId\":\"" + cartId + "\",\"cartLineId\":\""
                + secondLineId + "\",\"quantity\":3}";
        assertThat(policy.matchesMutationTarget(
                context("Remove the gray pair from my cart."), "remove_cart_line", secondArguments
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Remove the second line."), "remove_cart_line", secondArguments
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("2."), "remove_cart_line", secondArguments
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Set the second line quantity to 3."), "update_cart_line", secondArguments
        )).isTrue();

        String wrongArguments = "{\"cartId\":\"" + cartId + "\",\"cartLineId\":\""
                + firstLineId + "\"}";
        assertThat(policy.matchesMutationTarget(
                context("Remove the second line."), "remove_cart_line", wrongArguments
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("2."), "remove_cart_line", wrongArguments
        )).isFalse();
    }

    @Test
    void soleLineInTheLatestCompleteCartSnapshotBindsRemoveIt() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        CartSnapshotLine jacket = new CartSnapshotLine(
                lineId, "product-jacket-2", "offer-jacket-2", "Black cotton jacket");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(messageId, cartId, createdAt, List.of(jacket)),
                        cartLineSnapshot(messageId, 2, cartId, createdAt, jacket)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove it from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, lineId)
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("You know I don't like it, remove it from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, lineId)
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Remove this from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, lineId)
        )).isTrue();
    }

    @Test
    void singularCategoryUniquelyMatchesAPluralCartLineLabel() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        CartSnapshotLine hats = new CartSnapshotLine(
                UUID.randomUUID(),
                "product-hats",
                "offer-hats",
                "12 Richardson 112PFP Camo Trucker Hats"
        );
        CartSnapshotLine shoes = new CartSnapshotLine(
                UUID.randomUUID(), "product-shoes", "offer-shoes", "Gray trail shoes");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(messageId, cartId, createdAt, List.of(hats, shoes)),
                        cartLineSnapshot(messageId, 2, cartId, createdAt, hats),
                        cartLineSnapshot(messageId, 3, cartId, createdAt, shoes)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove the hat from my cart."),
                "remove_cart_line",
                removeLineArguments(cartId, hats.cartLineId())
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Remove the hat from my cart."),
                "remove_cart_line",
                removeLineArguments(cartId, shoes.cartLineId())
        )).isFalse();
    }

    @Test
    void cartLineDescriptionAndMetadataCanIdentifyAProductWhoseTitleDoesNotNameItsType() {
        UUID cartMessageId = UUID.randomUUID();
        UUID productMessageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant cartAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant productAt = cartAt.minusSeconds(60);
        CartSnapshotLine shirt = new CartSnapshotLine(
                UUID.randomUUID(), "product-shirt", "offer-shirt", "Oxford Button-Down");
        CartSnapshotLine hat = new CartSnapshotLine(
                UUID.randomUUID(), "product-hat", "offer-hat", "Camo Trucker");
        AgentArtifactReference shirtProduct = productWithMetadata(
                productMessageId,
                1,
                "product-shirt",
                "offer-shirt",
                "Oxford Button-Down",
                "A breathable everyday shirt",
                "Shirts"
        );
        AgentArtifactReference hatProduct = productWithMetadata(
                productMessageId,
                2,
                "product-hat",
                "offer-hat",
                "Camo Trucker",
                "A structured cap for sunny days",
                "Headwear"
        );
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(cartMessageId, cartId, cartAt, List.of(shirt, hat)),
                        cartLineSnapshot(cartMessageId, 2, cartId, cartAt, shirt),
                        cartLineSnapshot(cartMessageId, 3, cartId, cartAt, hat),
                        withCreatedAt(shirtProduct, productAt),
                        withCreatedAt(offer(productMessageId, 1, "product-shirt", "offer-shirt"), productAt),
                        withCreatedAt(hatProduct, productAt),
                        withCreatedAt(offer(productMessageId, 2, "product-hat", "offer-hat"), productAt)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove the shirt from my cart."),
                "remove_cart_line",
                removeLineArguments(cartId, shirt.cartLineId())
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Remove the shirt from my cart."),
                "remove_cart_line",
                removeLineArguments(cartId, hat.cartLineId())
        )).isFalse();
    }

    @Test
    void singularCategoryCannotChooseBetweenSeveralMatchingPluralCartLines() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        CartSnapshotLine first = new CartSnapshotLine(
                UUID.randomUUID(), "product-hat-1", "offer-hat-1", "Camo trucker hats");
        CartSnapshotLine second = new CartSnapshotLine(
                UUID.randomUUID(), "product-hat-2", "offer-hat-2", "Wool winter hat");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(messageId, cartId, createdAt, List.of(first, second)),
                        cartLineSnapshot(messageId, 2, cartId, createdAt, first),
                        cartLineSnapshot(messageId, 3, cartId, createdAt, second)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove the hat from my cart."),
                "remove_cart_line",
                removeLineArguments(cartId, first.cartLineId())
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Remove the hat from my cart."),
                "remove_cart_line",
                removeLineArguments(cartId, second.cartLineId())
        )).isFalse();
    }

    @Test
    void bareRemoveItCannotChooseBetweenMultipleLinesInTheLatestCartSnapshot() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        CartSnapshotLine first = new CartSnapshotLine(
                UUID.randomUUID(), "product-jacket-1", "offer-jacket-1", "Canvas field jacket");
        CartSnapshotLine second = new CartSnapshotLine(
                UUID.randomUUID(), "product-jacket-2", "offer-jacket-2", "Black cotton jacket");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(messageId, cartId, createdAt, List.of(first, second)),
                        cartLineSnapshot(messageId, 2, cartId, createdAt, first),
                        cartLineSnapshot(messageId, 3, cartId, createdAt, second)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove it from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, first.cartLineId())
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Remove it from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, second.cartLineId())
        )).isFalse();
    }

    @Test
    void unmatchedDescriptionCannotRemoveTheOnlyCurrentCartLine() {
        UUID messageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        CartSnapshotLine jacket = new CartSnapshotLine(
                lineId, "product-jacket", "offer-jacket", "Black cotton jacket");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(messageId, cartId, createdAt, List.of(jacket)),
                        cartLineSnapshot(messageId, 2, cartId, createdAt, jacket)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove that blue shirt from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, lineId)
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Remove that blue jacket from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, lineId)
        )).isFalse();
    }

    @Test
    void lineMissingFromANewerEmptyCartSnapshotIsStale() {
        UUID populatedMessageId = UUID.randomUUID();
        UUID emptyMessageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        Instant populatedAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant emptiedAt = Instant.parse("2026-07-19T10:01:00Z");
        CartSnapshotLine removed = new CartSnapshotLine(
                lineId, "product-jacket-2", "offer-jacket-2", "Black cotton jacket");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(emptyMessageId, cartId, emptiedAt, List.of()),
                        cartSnapshot(populatedMessageId, cartId, populatedAt, List.of(removed)),
                        cartLineSnapshot(populatedMessageId, 2, cartId, populatedAt, removed)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove the first item from the cart."),
                "remove_cart_line",
                removeLineArguments(cartId, lineId)
        )).isFalse();
    }

    @Test
    void secondJacketBindsItsOfferAndTheCurrentServerIssuedCartWithoutLiteralIds() {
        UUID productMessageId = UUID.randomUUID();
        UUID currentCartMessageId = UUID.randomUUID();
        UUID staleCartMessageId = UUID.randomUUID();
        UUID currentCartId = UUID.randomUUID();
        UUID staleCartId = UUID.randomUUID();
        Instant productAt = Instant.parse("2026-07-19T09:58:00Z");
        Instant staleCartAt = Instant.parse("2026-07-19T09:59:00Z");
        Instant currentCartAt = Instant.parse("2026-07-19T10:00:00Z");
        AgentArtifactReference firstProduct = productWithLabel(
                productMessageId, 1, "product-jacket-1", "offer-jacket-1", "Canvas field jacket");
        AgentArtifactReference secondProduct = productWithLabel(
                productMessageId, 2, "product-jacket-2", "offer-jacket-2", "Black cotton jacket");
        AgentArtifactReference firstOffer = offer(productMessageId, 1, "product-jacket-1", "offer-jacket-1");
        AgentArtifactReference secondOffer = offer(productMessageId, 2, "product-jacket-2", "offer-jacket-2");
        CartSnapshotLine existing = new CartSnapshotLine(
                UUID.randomUUID(), "product-jacket-1", "offer-jacket-1", "Canvas field jacket");
        AgentArtifactReference currentCart = cartSnapshot(
                currentCartMessageId, currentCartId, currentCartAt, List.of(existing));
        AgentArtifactReference currentLine = cartLineSnapshot(
                currentCartMessageId, 2, currentCartId, currentCartAt, existing);
        AgentArtifactReference staleCart = cartSnapshot(
                staleCartMessageId, staleCartId, staleCartAt, List.of());
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        currentCart,
                        currentLine,
                        staleCart,
                        withCreatedAt(firstProduct, productAt),
                        withCreatedAt(firstOffer, productAt),
                        withCreatedAt(secondProduct, productAt),
                        withCreatedAt(secondOffer, productAt)
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-1")).thenReturn(Optional.of(firstOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-2")).thenReturn(Optional.of(secondOffer));

        assertThat(policy.matchesMutationTarget(
                context("Put the second jacket into the cart."),
                "add_cart_line",
                addLineArguments(currentCartId, "offer-jacket-2")
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Put the second jacket into the cart."),
                "add_cart_line",
                addLineArguments(currentCartId, "offer-jacket-1")
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Put the second jacket into the cart."),
                "add_cart_line",
                addLineArguments(staleCartId, "offer-jacket-2")
        )).isFalse();
    }

    @Test
    void ordinalCartAdditionLoadsTheRecentArtifactWindowOnlyOnce() {
        UUID productMessageId = UUID.randomUUID();
        UUID cartMessageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant productAt = Instant.parse("2026-07-19T09:59:00Z");
        Instant cartAt = Instant.parse("2026-07-19T10:00:00Z");
        AgentArtifactReference secondOffer = offer(
                productMessageId, 2, "product-jacket-2", "offer-jacket-2");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(cartMessageId, cartId, cartAt, List.of()),
                        withCreatedAt(productWithLabel(
                                productMessageId,
                                1,
                                "product-jacket-1",
                                "offer-jacket-1",
                                "Canvas field jacket"
                        ), productAt),
                        withCreatedAt(productWithLabel(
                                productMessageId,
                                2,
                                "product-jacket-2",
                                "offer-jacket-2",
                                "Black cotton jacket"
                        ), productAt),
                        withCreatedAt(secondOffer, productAt)
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-2")).thenReturn(Optional.of(secondOffer));

        assertThat(policy.matchesMutationTarget(
                context("Put the second jacket into the cart."),
                "add_cart_line",
                addLineArguments(cartId, "offer-jacket-2")
        )).isTrue();
        verify(artifacts, times(1))
                .findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any());
    }

    @Test
    void addItAgainBindsOnlyTheUniquelyMostRecentlyRemovedOfferAndItsCurrentCart() {
        UUID productMessageId = UUID.randomUUID();
        UUID firstPopulatedMessageId = UUID.randomUUID();
        UUID firstEmptyMessageId = UUID.randomUUID();
        UUID secondPopulatedMessageId = UUID.randomUUID();
        UUID latestEmptyMessageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID otherCartId = UUID.randomUUID();
        Instant base = Instant.parse("2026-07-19T10:00:00Z");
        CartSnapshotLine earlierRemoval = new CartSnapshotLine(
                UUID.randomUUID(), "product-jacket-1", "offer-jacket-1", "Canvas field jacket");
        CartSnapshotLine latestRemoval = new CartSnapshotLine(
                UUID.randomUUID(), "product-jacket-2", "offer-jacket-2", "Black cotton jacket");
        AgentArtifactReference firstOffer = offer(productMessageId, 1, "product-jacket-1", "offer-jacket-1");
        AgentArtifactReference secondOffer = offer(productMessageId, 2, "product-jacket-2", "offer-jacket-2");
        UUID unrelatedMessageId = UUID.randomUUID();
        AgentArtifactReference unrelatedProduct = withCreatedAt(productWithLabel(
                unrelatedMessageId, 1, "product-shoe", "offer-shoe", "Blue trail shoe"),
                base.plusSeconds(5));
        AgentArtifactReference unrelatedOffer = withCreatedAt(
                offer(unrelatedMessageId, 1, "product-shoe", "offer-shoe"),
                base.plusSeconds(5));
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        unrelatedProduct,
                        unrelatedOffer,
                        cartSnapshot(latestEmptyMessageId, cartId, base.plusSeconds(4), List.of()),
                        cartSnapshot(secondPopulatedMessageId, cartId, base.plusSeconds(3), List.of(latestRemoval)),
                        cartLineSnapshot(secondPopulatedMessageId, 2, cartId, base.plusSeconds(3), latestRemoval),
                        cartSnapshot(firstEmptyMessageId, cartId, base.plusSeconds(2), List.of()),
                        cartSnapshot(firstPopulatedMessageId, cartId, base.plusSeconds(1), List.of(earlierRemoval)),
                        cartLineSnapshot(firstPopulatedMessageId, 2, cartId, base.plusSeconds(1), earlierRemoval),
                        withCreatedAt(productWithLabel(
                                productMessageId,
                                1,
                                "product-jacket-1",
                                "offer-jacket-1",
                                "Canvas field jacket"
                        ), base),
                        withCreatedAt(firstOffer, base),
                        withCreatedAt(productWithLabel(
                                productMessageId,
                                2,
                                "product-jacket-2",
                                "offer-jacket-2",
                                "Black cotton jacket"
                        ), base),
                        withCreatedAt(secondOffer, base)
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-1")).thenReturn(Optional.of(firstOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-2")).thenReturn(Optional.of(secondOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-shoe")).thenReturn(Optional.of(unrelatedOffer));

        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(cartId, "offer-jacket-2")
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Again, please."),
                "add_cart_line",
                addLineArguments(cartId, "offer-jacket-2")
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(cartId, "offer-jacket-1")
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(otherCartId, "offer-jacket-2")
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(cartId, "offer-shoe")
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-shoe\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-jacket-2\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Again, please."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-jacket-2\"}]}"
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Add the second jacket back."),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-jacket-2\"}]}"
        )).isFalse();
    }

    @Test
    void readdSkipsStaleAndAlreadyRestoredRemovalsBeforeAnOlderEligibleRemoval() {
        Instant base = Instant.parse("2026-07-19T10:00:00Z");
        UUID staleCartId = UUID.randomUUID();
        UUID replacementCartId = UUID.randomUUID();
        UUID restoredCartId = UUID.randomUUID();
        UUID eligibleCartId = UUID.randomUUID();
        CartSnapshotLine staleRemoval = new CartSnapshotLine(
                UUID.randomUUID(), "product-stale", "offer-stale", "Stale jacket");
        CartSnapshotLine restoredRemoval = new CartSnapshotLine(
                UUID.randomUUID(), "product-restored", "offer-restored", "Restored jacket");
        CartSnapshotLine eligibleRemoval = new CartSnapshotLine(
                UUID.randomUUID(), "product-eligible", "offer-eligible", "Eligible jacket");

        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(
                                UUID.randomUUID(), replacementCartId, base.plusSeconds(9), List.of(),
                                "SHOPIFY:replaced-route"),
                        cartSnapshot(
                                UUID.randomUUID(), staleCartId, base.plusSeconds(8), List.of(),
                                "SHOPIFY:replaced-route"),
                        cartSnapshot(
                                UUID.randomUUID(), staleCartId, base.plusSeconds(7), List.of(staleRemoval),
                                "SHOPIFY:replaced-route"),
                        cartSnapshot(
                                UUID.randomUUID(), restoredCartId, base.plusSeconds(6), List.of(restoredRemoval),
                                "SHOPIFY:restored-route"),
                        cartSnapshot(
                                UUID.randomUUID(), restoredCartId, base.plusSeconds(5), List.of(),
                                "SHOPIFY:restored-route"),
                        cartSnapshot(
                                UUID.randomUUID(), restoredCartId, base.plusSeconds(4), List.of(restoredRemoval),
                                "SHOPIFY:restored-route"),
                        cartSnapshot(
                                UUID.randomUUID(), eligibleCartId, base.plusSeconds(3), List.of(),
                                "SHOPIFY:eligible-route"),
                        cartSnapshot(
                                UUID.randomUUID(), eligibleCartId, base.plusSeconds(2), List.of(eligibleRemoval),
                                "SHOPIFY:eligible-route")
                ));

        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(eligibleCartId, "offer-eligible")
        )).isTrue();
    }

    @Test
    void cartLineOrdinalFailsClosedWhenSeveralCurrentCartsContainLines() {
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        UUID firstCartId = UUID.randomUUID();
        UUID secondCartId = UUID.randomUUID();
        CartSnapshotLine firstLine = new CartSnapshotLine(
                UUID.randomUUID(), "product-1", "offer-1", "Canvas field jacket");
        CartSnapshotLine secondLine = new CartSnapshotLine(
                UUID.randomUUID(), "product-2", "offer-2", "Black cotton jacket");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(
                                UUID.randomUUID(), firstCartId, createdAt.plusSeconds(1), List.of(firstLine),
                                "SHOPIFY:first-route"),
                        cartSnapshot(
                                UUID.randomUUID(), secondCartId, createdAt, List.of(secondLine),
                                "SHOPIFY:second-route")
                ));

        assertThat(policy.matchesMutationTarget(
                context("Remove the first line."),
                "remove_cart_line",
                removeLineArguments(firstCartId, firstLine.cartLineId())
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Remove the second line."),
                "remove_cart_line",
                removeLineArguments(secondCartId, secondLine.cartLineId())
        )).isFalse();
    }

    @Test
    void equalTimestampCartSnapshotsUseCanonicalMessageIdRegardlessOfInputOrder() {
        Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
        UUID preferredMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherMessageId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID preferredCartId = UUID.randomUUID();
        UUID otherCartId = UUID.randomUUID();
        CartSnapshotLine preferredLine = new CartSnapshotLine(
                UUID.randomUUID(), "product-preferred", "offer-preferred", "Preferred jacket");
        CartSnapshotLine otherLine = new CartSnapshotLine(
                UUID.randomUUID(), "product-other", "offer-other", "Other jacket");
        AgentArtifactReference preferred = cartSnapshot(
                preferredMessageId, preferredCartId, createdAt, List.of(preferredLine));
        AgentArtifactReference other = cartSnapshot(
                otherMessageId, otherCartId, createdAt, List.of(otherLine));

        for (List<AgentArtifactReference> input : List.of(List.of(preferred, other), List.of(other, preferred))) {
            when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                    .thenReturn(input);

            assertThat(policy.matchesMutationTarget(
                    context("Remove it from the cart."),
                    "remove_cart_line",
                    removeLineArguments(preferredCartId, preferredLine.cartLineId())
            )).isTrue();
            assertThat(policy.matchesMutationTarget(
                    context("Remove it from the cart."),
                    "remove_cart_line",
                    removeLineArguments(otherCartId, otherLine.cartLineId())
            )).isFalse();
        }
    }

    @Test
    void backInAnOrdinalPhraseDoesNotMasqueradeAsAReaddWithoutRemovalHistory() {
        UUID productMessageId = UUID.randomUUID();
        AgentArtifactReference firstOffer = offer(
                productMessageId, 1, "product-jacket-1", "offer-jacket-1");
        AgentArtifactReference secondOffer = offer(
                productMessageId, 2, "product-jacket-2", "offer-jacket-2");
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        productWithLabel(
                                productMessageId,
                                1,
                                "product-jacket-1",
                                "offer-jacket-1",
                                "Canvas field jacket"),
                        productWithLabel(
                                productMessageId,
                                2,
                                "product-jacket-2",
                                "offer-jacket-2",
                                "Black cotton jacket"),
                        firstOffer,
                        secondOffer
                ));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-1")).thenReturn(Optional.of(firstOffer));
        when(artifacts.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer-jacket-2")).thenReturn(Optional.of(secondOffer));

        String turn = "Go back to the second jacket and put it in the cart.";
        assertThat(policy.matchesMutationTarget(
                context(turn),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-jacket-2\"}]}"
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context(turn),
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-jacket-1\"}]}"
        )).isFalse();
    }

    @Test
    void readdUsesTheNewestSnapshotWhenTheSameCartGainsAuthoritativeRoutingMetadata() {
        UUID populatedMessageId = UUID.randomUUID();
        UUID emptyMessageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant populatedAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant emptiedAt = Instant.parse("2026-07-19T10:01:00Z");
        CartSnapshotLine removed = new CartSnapshotLine(
                UUID.randomUUID(), "product-jacket", "offer-jacket", "Black cotton jacket");
        AgentArtifactReference populatedWithoutRouting = withoutRoutingScope(
                cartSnapshot(populatedMessageId, cartId, populatedAt, List.of(removed)));
        when(artifacts.findByConversationIdOrderByCreatedAtDescOrdinalAsc(eq(CONVERSATION_ID), any()))
                .thenReturn(List.of(
                        cartSnapshot(emptyMessageId, cartId, emptiedAt, List.of()),
                        populatedWithoutRouting,
                        cartLineSnapshot(populatedMessageId, 2, cartId, populatedAt, removed)
                ));

        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(cartId, "offer-jacket")
        )).isTrue();
        assertThat(policy.matchesMutationTarget(
                context("Add it again."),
                "add_cart_line",
                addLineArguments(cartId, "offer-unrelated")
        )).isFalse();
        assertThat(policy.matchesMutationTarget(
                context("Remove it."),
                "remove_cart_line",
                removeLineArguments(cartId, removed.cartLineId())
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

    private AgentToolExecutionContext context(String text, AgentVisibleProductContext visibleProductContext) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(),
                CONVERSATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                text,
                null,
                null,
                null,
                visibleProductContext
        );
    }

    private AgentToolExecutionContext context(String text, AgentProductClarification clarification) {
        return context(text).withPendingProductClarification(clarification);
    }

    private AgentProductClarification clarification() {
        return new AgentProductClarification(
                "prepare_carts",
                "Add the hat I meant to my cart.",
                List.of(
                        new AgentVisibleProductReference(1, 5, "product-5", "offer-5", "Green cap"),
                        new AgentVisibleProductReference(2, 6, "product-6", "offer-6", "Brown cap"),
                        new AgentVisibleProductReference(3, 7, "product-7", "offer-7", "Red wool cap")
                )
        );
    }

    private AgentVisibleProductReference visibleProduct(int visibleOrdinal, int resultOrdinal) {
        return new AgentVisibleProductReference(
                visibleOrdinal,
                resultOrdinal,
                "product-" + resultOrdinal,
                "offer-" + resultOrdinal,
                "Product " + resultOrdinal
        );
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

    private AgentArtifactReference productWithMetadata(
            UUID messageId,
            int ordinal,
            String productKey,
            String offerKey,
            String label,
            String description,
            String productType
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
                .payloadJson("""
                        {"key":"%s","title":"%s","description":"%s",\
                        "attributes":[{"group":"apparel","name":"Product type","value":"%s"}],\
                        "offers":[{"key":"%s"}]}
                        """.formatted(productKey, label, description, productType, offerKey))
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

    private AgentArtifactReference cartSnapshot(
            UUID messageId,
            UUID cartId,
            Instant createdAt,
            List<CartSnapshotLine> lines
    ) {
        return cartSnapshot(messageId, cartId, createdAt, lines, "SHOPIFY:merchant-jackets");
    }

    private AgentArtifactReference cartSnapshot(
            UUID messageId,
            UUID cartId,
            Instant createdAt,
            List<CartSnapshotLine> lines,
            String routingScopeKey
    ) {
        int totalQuantity = lines.size();
        String amount = lines.isEmpty() ? "0.00" : totalQuantity == 1 ? "89.95" : "179.90";
        String linePayloads = String.join(",", lines.stream().map(this::cartLineJson).toList());
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .label("Cart at jackets.example")
                .cartId(cartId)
                .payloadJson("""
                        {"cartId":"%s","merchantDomain":"jackets.example","provider":"SHOPIFY",\
                        "merchantId":"merchant-jackets","routingScopeKey":"%s",\
                        "totalQuantity":%d,"totalAmount":"%s","subtotalAmount":"%s","currency":"USD",\
                        "lines":[%s],"appliedCodes":[],"deliveryGroups":[],"messages":[]}
                        """.formatted(cartId, routingScopeKey, totalQuantity, amount, amount, linePayloads))
                .createdAt(createdAt)
                .build();
    }

    private AgentArtifactReference cartLineSnapshot(
            UUID messageId,
            int ordinal,
            UUID cartId,
            Instant createdAt,
            CartSnapshotLine line
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.CART_LINE)
                .ordinal(ordinal)
                .stableKey("cart-line:" + line.cartLineId())
                .label(line.productTitle())
                .offerKey(line.offerKey())
                .cartId(cartId)
                .cartLineId(line.cartLineId())
                .payloadJson(cartLineJson(line))
                .createdAt(createdAt)
                .build();
    }

    private String cartLineJson(CartSnapshotLine line) {
        return """
                {"cartLineId":"%s","remoteCartLineId":"remote-%s","productId":"%s",\
                "productTitle":"%s","productVariantId":"variant-%s","variantTitle":"Medium",\
                "quantity":1,"unitAmount":"89.95","totalAmount":"89.95","currency":"USD",\
                "offerKey":"%s","provider":"SHOPIFY","merchantId":"merchant-jackets"}
                """.formatted(
                line.cartLineId(),
                line.cartLineId(),
                line.canonicalProductKey(),
                line.productTitle(),
                line.canonicalProductKey(),
                line.offerKey()
        ).strip();
    }

    private AgentArtifactReference withCreatedAt(AgentArtifactReference source, Instant createdAt) {
        return AgentArtifactReference.builder()
                .conversationId(source.getConversationId())
                .messageId(source.getMessageId())
                .artifactType(source.getArtifactType())
                .ordinal(source.getOrdinal())
                .stableKey(source.getStableKey())
                .label(source.getLabel())
                .canonicalProductKey(source.getCanonicalProductKey())
                .offerKey(source.getOfferKey())
                .inventoryItemId(source.getInventoryItemId())
                .cartId(source.getCartId())
                .cartLineId(source.getCartLineId())
                .checkoutAttemptId(source.getCheckoutAttemptId())
                .payloadJson(source.getPayloadJson())
                .createdAt(createdAt)
                .build();
    }

    private AgentArtifactReference withRunId(AgentArtifactReference source, UUID runId) {
        return AgentArtifactReference.builder()
                .conversationId(source.getConversationId())
                .messageId(source.getMessageId())
                .runId(runId)
                .artifactType(source.getArtifactType())
                .ordinal(source.getOrdinal())
                .stableKey(source.getStableKey())
                .label(source.getLabel())
                .canonicalProductKey(source.getCanonicalProductKey())
                .offerKey(source.getOfferKey())
                .inventoryItemId(source.getInventoryItemId())
                .cartId(source.getCartId())
                .cartLineId(source.getCartLineId())
                .checkoutAttemptId(source.getCheckoutAttemptId())
                .payloadJson(source.getPayloadJson())
                .createdAt(source.getCreatedAt())
                .build();
    }

    private AgentArtifactReference withoutRoutingScope(AgentArtifactReference source) {
        return AgentArtifactReference.builder()
                .conversationId(source.getConversationId())
                .messageId(source.getMessageId())
                .artifactType(source.getArtifactType())
                .ordinal(source.getOrdinal())
                .stableKey(source.getStableKey())
                .label(source.getLabel())
                .canonicalProductKey(source.getCanonicalProductKey())
                .offerKey(source.getOfferKey())
                .inventoryItemId(source.getInventoryItemId())
                .cartId(source.getCartId())
                .cartLineId(source.getCartLineId())
                .checkoutAttemptId(source.getCheckoutAttemptId())
                .payloadJson(source.getPayloadJson().replace(
                        "\"routingScopeKey\":\"SHOPIFY:merchant-jackets\",", ""))
                .createdAt(source.getCreatedAt())
                .build();
    }

    private String removeLineArguments(UUID cartId, UUID cartLineId) {
        return "{\"cartId\":\"" + cartId + "\",\"cartLineId\":\"" + cartLineId + "\"}";
    }

    private String addLineArguments(UUID cartId, String offerKey) {
        return "{\"cartId\":\"" + cartId + "\",\"offerKey\":\"" + offerKey + "\",\"quantity\":1}";
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

    private record CartSnapshotLine(
            UUID cartLineId,
            String canonicalProductKey,
            String offerKey,
            String productTitle
    ) {
    }
}
