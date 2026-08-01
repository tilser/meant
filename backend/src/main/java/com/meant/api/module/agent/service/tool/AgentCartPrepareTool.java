package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import com.meant.api.module.agent.service.dto.AgentCartToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentCartPrepareTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "prepare_carts",
            "Prepare carts from exact server-issued offers, reusing the newest compatible current cart for each "
                    + "merchant/provider route and creating one only when none exists. Prefer add_cart_line when "
                    + "the target cart is already known. For a buyer-requested size, color, or other configuration, "
                    + "use only the selectedOfferKey returned by select_product_variant after an exact cartable "
                    + "match in the current run. Never substitute a default or anchor offer. Configurable offers are "
                    + "rejected without that proof, and all offers are revalidated before every mutation.",
            """
                    {"type":"object","additionalProperties":false,"required":["offers"],"properties":{"offers":{"type":"array","minItems":1,"maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["offerKey"],"properties":{"offerKey":{"type":"string","minLength":1,"maxLength":200},"quantity":{"type":"integer","minimum":1,"maximum":1000}}}}}}
                    """,
            "1.0",
            AgentToolRisk.REVERSIBLE_MUTATION
    );

    private final AgentCartToolSupport support;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentCartToolArguments.Prepare arguments = support.arguments(
                argumentsJson, AgentCartToolArguments.Prepare.class);
        AgentCartResult result = support.prepare(context, arguments);
        String resultJson = support.json(result);
        return new AgentToolExecutionResult(
                resultJson,
                safeSummary(result.carts().size(), result.failures().size()),
                support.artifacts(result),
                result.carts().isEmpty() ? null : AgentRunEventType.CART_CHANGED
        );
    }

    static String safeSummary(int preparedCount, int failureCount) {
        String prepared = "Prepared " + preparedCount + " cart(s)";
        if (failureCount == 0) {
            return prepared + ".";
        }
        return prepared + "; " + failureCount + " route(s) failed.";
    }
}
