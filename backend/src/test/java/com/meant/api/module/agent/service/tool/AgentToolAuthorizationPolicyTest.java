package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentToolAuthorizationPolicyTest {

    private final AgentToolAuthorizationPolicy policy = new AgentToolAuthorizationPolicy();

    @Test
    void modelLaneAvailabilityDependsOnlyOnRiskAndNotOnUserLanguage() {
        List<AgentToolDescriptor> descriptors = List.of(
                descriptor("search_catalog", AgentToolRisk.READ),
                descriptor("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION),
                descriptor("prepare_checkout", AgentToolRisk.CHECKOUT_PREPARATION),
                descriptor("place_order", AgentToolRisk.IRREVERSIBLE_MUTATION)
        );

        for (String text : List.of(
                "Add the blue one to my cart.",
                "Přidej modrou variantu do košíku.",
                "Lege die blaue Variante in den Warenkorb.",
                "これは購入の依頼です"
        )) {
            assertThat(policy.available(modelContext(text), descriptors))
                    .extracting(AgentToolDescriptor::name)
                    .containsExactly("search_catalog", "prepare_carts");
        }
    }

    @Test
    void explicitUserActionLaneCanPrepareCheckoutButCannotExecuteIrreversibleMutation() {
        AgentToolExecutionContext context = userActionContext();

        assertThat(policy.authorized(context,
                descriptor("prepare_checkout", AgentToolRisk.CHECKOUT_PREPARATION))).isTrue();
        assertThat(policy.authorized(context,
                descriptor("place_order", AgentToolRisk.IRREVERSIBLE_MUTATION))).isFalse();
    }

    @Test
    void modelLaneCannotPrepareCheckoutEvenWhenUserTextRequestsIt() {
        assertThat(policy.authorized(
                modelContext("Prepare checkout now."),
                descriptor("prepare_checkout", AgentToolRisk.CHECKOUT_PREPARATION)
        )).isFalse();
    }

    private AgentToolExecutionContext modelContext(String text) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), text);
    }

    private AgentToolExecutionContext userActionContext() {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "approved action");
    }

    private AgentToolDescriptor descriptor(String name, AgentToolRisk risk) {
        return new AgentToolDescriptor(name, name, "{\"type\":\"object\"}", "v1", risk);
    }
}
