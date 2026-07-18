package com.meant.api.module.agent.service;

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
            "Prepare one owned checkout per merchant cart and return explicit user-facing next actions. This tool never opens or completes checkout and never handles payment credentials.",
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
