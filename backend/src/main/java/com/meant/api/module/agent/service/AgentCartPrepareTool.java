package com.meant.api.module.agent.service;

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
            "Revalidate exact server-issued offer keys, partition them by merchant/provider route, and prepare one cart per route. Partial merchant failures are returned alongside successful carts.",
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
                "Prepared " + result.carts().size() + " cart(s); " + result.failures().size() + " route(s) failed.",
                support.artifacts(result),
                result.carts().isEmpty() ? null : AgentRunEventType.CART_CHANGED
        );
    }
}
