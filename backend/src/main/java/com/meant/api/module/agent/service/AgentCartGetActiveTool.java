package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import com.meant.api.module.agent.service.dto.AgentCartToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.cart.service.dto.CartResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentCartGetActiveTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_active_carts",
            "List the current user's active, unexpired carts using concise local cart and line references.",
            """
                    {"type":"object","additionalProperties":false,"properties":{"limit":{"type":"integer","minimum":1,"maximum":20}}}
                    """,
            "1.0",
            AgentToolRisk.READ
    );

    private final AgentCartToolSupport support;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentCartToolArguments.GetActive arguments = support.arguments(
                argumentsJson, AgentCartToolArguments.GetActive.class);
        List<CartResult> carts = support.active(context, arguments);
        AgentCartResult result = AgentCartResult.success(carts);
        String resultJson = support.json(result);
        return AgentToolExecutionResult.read(
                resultJson,
                "Loaded " + carts.size() + " current cart(s).",
                support.artifacts(result)
        );
    }
}
