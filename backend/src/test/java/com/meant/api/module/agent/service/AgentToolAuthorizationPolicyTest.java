package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentToolAuthorizationPolicyTest {

    private final ShoppingMissionRepository missions = mock(ShoppingMissionRepository.class);
    private final AgentMutationTargetPolicy targetPolicy = mock(AgentMutationTargetPolicy.class);
    private final AgentToolAuthorizationPolicy policy = new AgentToolAuthorizationPolicy(missions, targetPolicy);

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
        assertThat(policy.authorized(directContext(), descriptor)).isTrue();
    }

    @Test
    void bareOrAttributeOnlyCartLanguageCannotAuthorizeAnArbitraryOffer() {
        AgentToolDescriptor descriptor = descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION);

        assertThat(policy.authorized(context("Add to cart."), descriptor)).isFalse();
        assertThat(policy.authorized(context("Add the blue shoes to my cart."), descriptor)).isFalse();
        assertThat(policy.authorized(context("Buy this one."), descriptor)).isFalse();
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
