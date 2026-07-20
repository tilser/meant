package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentCheckoutResult;
import com.meant.api.module.agent.service.dto.AgentCheckoutToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentCheckoutGetTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_checkout",
            "Get an already-prepared checkout for an owned cart. This read tool never creates, opens, or completes a checkout.",
            """
                    {"type":"object","additionalProperties":false,"required":["cartId"],"properties":{"cartId":{"type":"string","format":"uuid"},"refresh":{"type":"boolean"}}}
                    """,
            "1.0",
            AgentToolRisk.READ
    );

    private final AgentCheckoutToolSupport support;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentCheckoutToolArguments.Get arguments = support.arguments(
                argumentsJson, AgentCheckoutToolArguments.Get.class);
        AgentCheckoutResult result = AgentCheckoutResult.success(List.of(support.get(context, arguments)));
        String resultJson = support.json(result);
        return AgentToolExecutionResult.read(
                resultJson, "Loaded the prepared checkout.", support.artifacts(result));
    }
}
