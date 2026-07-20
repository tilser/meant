package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.AgentMutationTargetPolicy;
import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentToolAuthorizationPolicyTest {

    private final ShoppingMissionRepository missions = mock(ShoppingMissionRepository.class);
    private final AgentMutationTargetPolicy targetPolicy = mock(AgentMutationTargetPolicy.class);
    private final AgentMessageRepository messages = mock(AgentMessageRepository.class);
    private final AgentToolAuthorizationPolicy policy = new AgentToolAuthorizationPolicy(
            missions, targetPolicy, messages);

    @Test
    void broadShoppingLanguageDoesNotAuthorizeAnUnrequestedCartMutation() {
        assertThat(policy.authorized(
                context("I wanna buy new clothes."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void singleProductTravelRequestUsesDiscoveryInsteadOfCreatingAMission() {
        AgentToolExecutionContext context = context("I am going to Spain, I need swimming shorts");

        assertThat(policy.authorized(
                context,
                descriptor("search_catalog", AgentToolRisk.READ)
        )).isTrue();
        assertThat(policy.authorized(
                context,
                descriptor("create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void singleProductRequestDoesNotBecomeAMissionJustBecauseItMentionsATrip() {
        assertThat(policy.authorized(
                context("Prepare for my Spain trip by finding swimming shorts."),
                descriptor("create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void explicitMultiItemPlanningRequestCanCreateAMission() {
        assertThat(policy.authorized(
                context("Plan everything I need for a summer picnic."),
                descriptor("create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
        assertThat(policy.authorized(
                context("Plan a summer picnic in San Francisco."),
                descriptor("create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
        assertThat(policy.authorized(
                context("Could you create a shopping mission for me?"),
                descriptor("create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    @Test
    void negatedOrSingleItemPlanningLanguageCannotCreateAMission() {
        AgentToolDescriptor mission = descriptor(
                "create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION);

        assertThat(policy.authorized(
                context("Don't create a shopping mission; just find swimming shorts."), mission
        )).isFalse();
        assertThat(policy.authorized(
                context("Create a plan for one swimsuit."), mission
        )).isFalse();
        assertThat(policy.authorized(
                context("How do I create a shopping mission?"), mission
        )).isFalse();
        assertThat(policy.authorized(
                context("Plan a picnic blanket purchase."), mission
        )).isFalse();
    }

    @Test
    void anExplicitOutfitGoalCanCreateAMission() {
        assertThat(policy.authorized(
                context("Build me a summer outfit."),
                descriptor("create_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    @Test
    void anOldActiveMissionCannotBeChangedByAnUnrelatedProductSearch() {
        ShoppingMission mission = activeMission();
        when(missions.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mission));

        AgentToolExecutionContext search = context("Find swimming shorts for Spain.");
        assertThat(policy.authorized(
                search,
                descriptor("update_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                search,
                descriptor("evaluate_mission_coverage", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("Proceed."),
                descriptor("update_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("Proceed with the mission."),
                descriptor("update_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
        assertThat(policy.authorized(
                context("Can you explain how to update the mission?"),
                descriptor("update_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void anExplicitMissionUpdateMustTargetTheActiveMission() {
        ShoppingMission mission = activeMission();
        when(missions.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mission));
        AgentToolDescriptor update = descriptor(
                "update_shopping_mission", AgentToolRisk.REVERSIBLE_MUTATION);
        AgentToolExecutionContext context = context("Add sunscreen to the mission.");
        String arguments = "{\"missionId\":\"" + mission.getId() + "\"}";

        assertThat(policy.authorized(context, update)).isTrue();
        when(targetPolicy.matchesMissionTarget(mission, arguments)).thenReturn(true);
        assertThat(policy.authorizedInvocation(context, update, arguments)).isTrue();

        String otherArguments = "{\"missionId\":\"" + UUID.randomUUID() + "\"}";
        assertThat(policy.authorizedInvocation(context, update, otherArguments)).isFalse();
    }

    @Test
    void compoundDiscoveryCartAndCheckoutRequestRetainsBothRequestedMutations() {
        AgentToolExecutionContext context = context(
                "find a black SF hat and put it into cart, prepare the checkout for me"
        );

        assertThat(policy.authorized(
                context,
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
        assertThat(policy.authorized(
                context,
                descriptor("prepare_checkout", AgentToolRisk.CHECKOUT_PREPARATION)
        )).isTrue();
    }

    @Test
    void discoveryPlusOneMutationDoesNotAuthorizeAnUnrelatedMentionedMutation() {
        assertThat(policy.authorized(
                context("Find a black hat and save it while explaining how checkout works."),
                descriptor("prepare_checkout", AgentToolRisk.CHECKOUT_PREPARATION)
        )).isFalse();
    }

    @Test
    void exactFollowUpAndDirectClickAuthorizeTheSameCartCapability() {
        AgentToolDescriptor descriptor = descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION);

        assertThat(policy.authorized(context("Add the second one."), descriptor)).isTrue();
        assertThat(policy.authorized(context("Add the gray pair to my cart."), descriptor)).isTrue();
        assertThat(policy.authorized(directContext(), descriptor)).isTrue();
    }

    @Test
    void cartLanguageExposesTheCapabilityButCannotAuthorizeAnUnprovenTarget() {
        AgentToolDescriptor descriptor = descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION);
        when(targetPolicy.matchesMutationTarget(any(), eq("prepare_carts"), anyString())).thenReturn(false);

        assertThat(policy.authorized(context("Add to cart."), descriptor)).isTrue();
        assertThat(policy.authorizedInvocation(
                context("Add the blue shoes to my cart."),
                descriptor,
                "{\"offers\":[{\"offerKey\":\"offer-2\"}]}"
        )).isFalse();
        assertThat(policy.authorizedInvocation(
                context("Buy this one."),
                descriptor,
                "{\"offers\":[{\"offerKey\":\"offer-2\"}]}"
        )).isFalse();
    }

    @Test
    void unrelatedDetailLanguageDoesNotExposeCartMutationTools() {
        assertThat(policy.authorized(
                context("Get more details on the second one."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void informationalQuestionAboutCartImpactDoesNotAuthorizeMutation() {
        assertThat(policy.authorized(
                context("How much would the gray pair add to my cart?"),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("I'd like to know whether the gray pair would add to my cart."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void politeDirectCartRequestRemainsAuthorized() {
        assertThat(policy.authorized(
                context("Could you add the gray pair to my cart?"),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    @Test
    void naturalFollowUpsExposeExistingCartMutations() {
        assertThat(policy.authorized(
                context("You know I don't like it, remove it from the cart."),
                descriptor("remove_cart_line", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
        assertThat(policy.authorized(
                context("Add it again."),
                descriptor("add_cart_line", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    @Test
    void numberedAnswerToImmediateCartRemovalClarificationAuthorizesTheSelectedLine() {
        UUID conversationId = UUID.randomUUID();
        UUID triggeringMessageId = UUID.randomUUID();
        AgentToolExecutionContext context = context(conversationId, triggeringMessageId, "1.");
        AgentToolDescriptor remove = descriptor("remove_cart_line", AgentToolRisk.REVERSIBLE_MUTATION);
        AgentMessage triggering = message(
                triggeringMessageId, conversationId, AgentMessageRole.USER, 12, "1.");
        AgentMessage clarification = message(
                UUID.randomUUID(),
                conversationId,
                AgentMessageRole.ASSISTANT,
                11,
                "Which cart item should I remove?\n1. Camo trucker hats\n2. Wool winter hat"
        );
        when(messages.findById(triggeringMessageId)).thenReturn(Optional.of(triggering));
        when(messages.findFirstByConversationIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                conversationId, 12)).thenReturn(Optional.of(clarification));
        when(targetPolicy.matchesMutationTarget(context, "remove_cart_line", "{\"cartLineId\":\"line-1\"}"))
                .thenReturn(true);

        assertThat(policy.authorized(context, remove)).isTrue();
        assertThat(policy.authorizedInvocation(
                context, remove, "{\"cartLineId\":\"line-1\"}"
        )).isTrue();
    }

    @Test
    void bareNumberDoesNotAuthorizeRemovalWithoutAnImmediateRemovalClarification() {
        UUID conversationId = UUID.randomUUID();
        UUID triggeringMessageId = UUID.randomUUID();
        AgentToolExecutionContext context = context(conversationId, triggeringMessageId, "1.");
        AgentMessage triggering = message(
                triggeringMessageId, conversationId, AgentMessageRole.USER, 12, "1.");
        AgentMessage unrelatedQuestion = message(
                UUID.randomUUID(), conversationId, AgentMessageRole.ASSISTANT, 11, "Which color do you prefer?");
        when(messages.findById(triggeringMessageId)).thenReturn(Optional.of(triggering));
        when(messages.findFirstByConversationIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                conversationId, 12)).thenReturn(Optional.of(unrelatedQuestion));

        assertThat(policy.authorized(
                context,
                descriptor("remove_cart_line", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void conversationalContinueDoesNotDelegateCommerceButAShortApprovalCan() {
        AgentToolDescriptor checkout = descriptor("prepare_checkout", AgentToolRisk.CHECKOUT_PREPARATION);

        assertThat(policy.authorized(context("Continue explaining the sizing details."), checkout)).isFalse();
        assertThat(policy.authorized(context("Proceed."), checkout)).isTrue();
        assertThat(policy.authorized(context("lets do checkout"), checkout)).isTrue();
        assertThat(policy.authorized(context("Let's do the checkout."), checkout)).isTrue();
        assertThat(policy.authorized(context("Let's talk about checkout."), checkout)).isFalse();
    }

    @Test
    void aDelegatedActiveMissionCanPrepareItsBundle() {
        ShoppingMission mission = activeMission();
        when(missions.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(Optional.of(mission));

        assertThat(policy.authorized(
                context("Prepare everything I need for the picnic."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    @Test
    void onlyAnUnqualifiedDelegatedCartAdditionBypassesProductClarification() {
        ShoppingMission mission = activeMission();
        when(missions.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mission));
        AgentToolExecutionContext context = context(
                "Prepare everything I need for the picnic; go ahead through checkout."
        );
        AgentToolDescriptor prepareCarts = descriptor(
                "prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION);
        String arguments = "{\"offers\":[{\"offerKey\":\"offer-outside-mission\"}]}";

        assertThat(policy.isUnqualifiedDelegatedCartAddition(context, "prepare_carts")).isTrue();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(context, "prepare_checkout")).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Add the second one to my cart."), "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; add the third blue one to my cart."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; choose the blue one."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; I want the blue one."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; choose 3."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; add #3."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; #3."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything; the blue one."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything except that one."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything with the blue cap."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything I need for the picnic with the blue one."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything for the picnic except the blue one."),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("Prepare everything I need; the Blue cap is the target.")
                        .withVisibleProductContext(new AgentVisibleProductContext(
                                UUID.randomUUID(),
                                List.of(new AgentVisibleProductReference(
                                        3, 3, "product-blue", "offer-blue", "Blue cap"))
                        )),
                "prepare_carts"
        )).isFalse();
        assertThat(policy.authorizedInvocation(context, prepareCarts, arguments)).isFalse();
        verify(targetPolicy).matchesDelegatedMission(
                mission, "prepare_carts", arguments);
    }

    @Test
    void pendingProductClarificationIsNeverTreatedAsAnUnqualifiedMissionAddition() {
        ShoppingMission mission = activeMission();
        when(missions.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Optional.of(mission));
        AgentProductClarification pending = new AgentProductClarification(
                "prepare_carts",
                "Prepare everything I need for the picnic.",
                List.of(new AgentVisibleProductReference(
                        2, 2, "product-blue", "offer-blue", "Blue cap"))
        );

        assertThat(policy.isUnqualifiedDelegatedCartAddition(
                context("2.").withPendingProductClarification(pending),
                "prepare_carts"
        )).isFalse();
    }

    private ShoppingMission activeMission() {
        ShoppingMission mission = mock(ShoppingMission.class);
        when(mission.getId()).thenReturn(UUID.randomUUID());
        when(mission.getGoal()).thenReturn("Prepare a complete picnic");
        when(mission.getStatus()).thenReturn(ShoppingMissionStatus.READY);
        return mission;
    }

    @Test
    void readsRemainAvailableWithoutMutationLanguage() {
        assertThat(policy.authorized(
                context("What would work for summer?"),
                descriptor("search_catalog", AgentToolRisk.READ)
        )).isTrue();
    }

    @Test
    void aNegatedInstructionNeverAuthorizesTheMentionedMutation() {
        assertThat(policy.authorized(
                context("Don't add the second one to my cart."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void negationInAnEarlierClauseDoesNotBlockALaterExplicitMutation() {
        assertThat(policy.authorized(
                context("I don't want the red one — add the second one to my cart."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    @Test
    void productMutationNamesUseWholeWordsAndMatchTheExactCapability() {
        assertThat(policy.authorized(
                context("Show me a pink shirt."),
                descriptor("pin_product", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("Show me watches."),
                descriptor("watch_product", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("Watch the first one."),
                descriptor("pin_product", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("Stop watching the first one."),
                descriptor("watch_product", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void productAndCartLineMutationsStillRequireAProvenInvocationTarget() {
        AgentToolDescriptor pin = descriptor("pin_product", AgentToolRisk.REVERSIBLE_MUTATION);
        AgentToolDescriptor remove = descriptor("remove_cart_line", AgentToolRisk.REVERSIBLE_MUTATION);
        when(targetPolicy.matchesMutationTarget(any(), eq("pin_product"), anyString())).thenReturn(false);
        when(targetPolicy.matchesMutationTarget(any(), eq("remove_cart_line"), anyString())).thenReturn(false);

        assertThat(policy.authorizedInvocation(
                context("Pin the gray pair."), pin, "{\"canonicalProductKey\":\"product-2\"}"
        )).isFalse();
        assertThat(policy.authorizedInvocation(
                context("Remove the gray pair."), remove,
                "{\"cartId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"cartLineId\":\"00000000-0000-0000-0000-000000000002\"}"
        )).isFalse();
    }

    @Test
    void neverLanguageDoesNotAuthorizeAMutation() {
        assertThat(policy.authorized(
                context("Never add the second one."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
    }

    @Test
    void aBareClarificationAnswerReusesIntentOnlyForTheRecordedTool() {
        AgentToolDescriptor prepareCarts = descriptor(
                "prepare_carts",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        AgentProductClarification pending = new AgentProductClarification(
                "prepare_carts",
                "Please add the blue hat to my cart.",
                List.of(new AgentVisibleProductReference(
                        2,
                        6,
                        "product-6",
                        "offer-6",
                        "Sky blue hat"
                ))
        );
        AgentToolExecutionContext answer = context("2.")
                .withPendingProductClarification(pending);
        when(targetPolicy.isPendingProductSelectionAnswer(answer, "prepare_carts"))
                .thenReturn(true);
        when(targetPolicy.matchesMutationTarget(
                answer,
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).thenReturn(true);

        assertThat(policy.authorized(answer, prepareCarts)).isTrue();
        assertThat(policy.authorizedInvocation(
                answer,
                prepareCarts,
                "{\"offers\":[{\"offerKey\":\"offer-6\"}]}"
        )).isTrue();
        assertThat(policy.authorized(
                answer,
                descriptor("pin_product", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isFalse();
        assertThat(policy.authorized(
                context("2."),
                prepareCarts
        )).isFalse();
    }

    @Test
    void aNewOrCancelledRequestCannotReusePendingMutationConsent() {
        AgentToolDescriptor prepareCarts = descriptor(
                "prepare_carts",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        AgentProductClarification pending = new AgentProductClarification(
                "prepare_carts",
                "Please add the blue hat to my cart.",
                List.of(new AgentVisibleProductReference(
                        1,
                        1,
                        "product-blue",
                        "offer-blue",
                        "Blue hat"
                ))
        );

        assertThat(policy.authorized(
                context("Show me the blue hat.").withPendingProductClarification(pending),
                prepareCarts
        )).isFalse();
        assertThat(policy.authorized(
                context("Never mind.").withPendingProductClarification(pending),
                prepareCarts
        )).isFalse();
        assertThat(policy.authorized(
                context("Don't add it; show me details.").withPendingProductClarification(pending),
                prepareCarts
        )).isFalse();
    }

    @Test
    void pendingCartAdditionConsentCanContinueWithAnExistingCartTool() {
        AgentProductClarification pending = new AgentProductClarification(
                "prepare_carts",
                "Please add the blue hat to my cart.",
                List.of(new AgentVisibleProductReference(
                        2,
                        6,
                        "product-6",
                        "offer-6",
                        "Sky blue hat"
                ))
        );
        AgentToolExecutionContext answer = context("2. Sky blue hat")
                .withPendingProductClarification(pending);
        when(targetPolicy.isPendingProductSelectionAnswer(answer, "add_cart_line"))
                .thenReturn(true);

        assertThat(policy.authorized(
                answer,
                descriptor("add_cart_line", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
    }

    private AgentToolDescriptor descriptor(String name, AgentToolRisk risk) {
        return new AgentToolDescriptor(
                name,
                name,
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                risk
        );
    }

    private AgentToolExecutionContext context(String text) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                text
        );
    }

    private AgentToolExecutionContext context(UUID conversationId, UUID triggeringMessageId, String text) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), conversationId, UUID.randomUUID(), triggeringMessageId, text);
    }

    private AgentMessage message(
            UUID id,
            UUID conversationId,
            AgentMessageRole role,
            long sequenceNumber,
            String text
    ) {
        return AgentMessage.builder()
                .id(id)
                .conversationId(conversationId)
                .role(role)
                .contentKind(AgentContentKind.TEXT)
                .sequenceNumber(sequenceNumber)
                .textContent(text)
                .build();
    }

    private AgentToolExecutionContext directContext() {
        return new AgentToolExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Clicked Add to cart"
        );
    }
}
