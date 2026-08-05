package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Structural tool gate. Language and inferred intent never participate in authorization.
 * Checkout session preparation and updates do not submit payment, so they are available to the model.
 * Irreversible commerce mutations remain unavailable in every agent execution lane.
 */
@Service
public class AgentToolAuthorizationPolicy {

    private static final Set<String> PERMANENT_ACCOUNT_TOOLS = Set.of(
            "get_user_preferences",
            "search_inventory",
            "get_inventory_item",
            "list_saved_products",
            "list_product_interactions",
            "list_recent_orders",
            "get_order",
            "pin_product",
            "unpin_product",
            "watch_product",
            "unwatch_product",
            "prepare_checkout",
            "get_checkout",
            "update_checkout"
    );

    public List<AgentToolDescriptor> available(
            AgentToolExecutionContext context,
            List<AgentToolDescriptor> descriptors
    ) {
        return descriptors.stream()
                .filter(descriptor -> authorized(context, descriptor))
                .toList();
    }

    public boolean authorized(AgentToolExecutionContext context, AgentToolDescriptor descriptor) {
        if (context == null || descriptor == null) {
            return false;
        }
        if (context.anonymousUser() && PERMANENT_ACCOUNT_TOOLS.contains(descriptor.name())) {
            return false;
        }
        return descriptor.riskClass() != AgentToolRisk.IRREVERSIBLE_MUTATION;
    }

    /** Invocation arguments are validated by {@code ReferenceIntegrityPolicy}; this gate remains risk-only. */
    public boolean authorizedInvocation(
            AgentToolExecutionContext context,
            AgentToolDescriptor descriptor,
            String argumentsJson
    ) {
        return authorized(context, descriptor);
    }
}
