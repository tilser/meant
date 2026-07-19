package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
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
    }

    @Test
    void aDelegatedActiveMissionCanPrepareItsBundle() {
        ShoppingMission mission = mock(ShoppingMission.class);
        when(mission.getStatus()).thenReturn(ShoppingMissionStatus.READY);
        when(missions.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(Optional.of(mission));

        assertThat(policy.authorized(
                context("Prepare everything I need for the picnic."),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION)
        )).isTrue();
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
