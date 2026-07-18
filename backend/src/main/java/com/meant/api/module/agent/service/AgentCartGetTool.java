package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import com.meant.api.module.agent.service.dto.AgentCartToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentCartGetTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_cart",
            "Get an owned cart by its stable Meant cart ID. Refresh only when current merchant state is required.",
            """
                    {"type":"object","additionalProperties":false,"required":["cartId"],"properties":{"cartId":{"type":"string","format":"uuid"},"refresh":{"type":"boolean"}}}
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
        AgentCartToolArguments.Get arguments = support.arguments(argumentsJson, AgentCartToolArguments.Get.class);
        AgentCartResult result = AgentCartResult.success(List.of(support.get(context, arguments)));
        String resultJson = support.json(result);
        return AgentToolExecutionResult.read(resultJson, "Loaded the requested cart.", support.artifacts(result));
    }
}
