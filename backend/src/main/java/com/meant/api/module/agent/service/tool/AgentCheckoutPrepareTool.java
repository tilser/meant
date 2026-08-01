package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentCheckoutResult;
import com.meant.api.module.agent.service.dto.AgentCheckoutToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentCheckoutPrepareTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "prepare_checkout",
            "Create or refresh one owned merchant checkout session per selected cart and return explicit "
                    + "user-facing next actions. The result renders the checkout UI in this conversation. "
                    + "When the merchant requests contact or shipping details, the tool automatically reuses the "
                    + "authenticated user's saved checkout details when available and reports whether they were "
                    + "applied without exposing their values to the model. "
                    + "Each cart is checked out as a whole, including all of its current lines. Use cart IDs from "
                    + "the authoritative current commerce state or get_active_carts; never ask for or pass product "
                    + "descriptions, offer keys, or cart-line IDs. This tool never submits payment and never handles "
                    + "payment credentials.",
            """
                    {"type":"object","additionalProperties":false,"required":["cartIds"],"properties":{"cartIds":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"string","format":"uuid"}}}}
                    """,
            "1.0",
            AgentToolRisk.CHECKOUT_PREPARATION
    );

    private final AgentCheckoutToolSupport support;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentCheckoutToolArguments.Prepare arguments = support.arguments(
                argumentsJson, AgentCheckoutToolArguments.Prepare.class);
        AgentCheckoutResult result = support.prepare(context, arguments);
        String resultJson = support.json(result);
        return new AgentToolExecutionResult(
                resultJson,
                "Prepared " + result.checkouts().size() + " checkout(s); "
                        + result.failures().size() + " cart(s) failed.",
                support.artifacts(result),
                result.checkouts().isEmpty() ? null : AgentRunEventType.CHECKOUT_READY
        );
    }
}
