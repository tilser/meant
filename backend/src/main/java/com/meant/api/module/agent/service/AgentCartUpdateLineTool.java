package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunEventType;
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
public class AgentCartUpdateLineTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "update_cart_line",
            "Update the quantity of an owned cart line using its stable Meant cart-line ID.",
            """
                    {"type":"object","additionalProperties":false,"required":["cartId","cartLineId","quantity"],"properties":{"cartId":{"type":"string","format":"uuid"},"cartLineId":{"type":"string","format":"uuid"},"quantity":{"type":"integer","minimum":1,"maximum":1000}}}
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
        AgentCartToolArguments.UpdateLine arguments = support.arguments(
                argumentsJson, AgentCartToolArguments.UpdateLine.class);
        AgentCartResult result = AgentCartResult.success(List.of(support.updateLine(context, arguments)));
        String resultJson = support.json(result);
        return new AgentToolExecutionResult(
                resultJson, "Updated the cart-line quantity.", support.artifacts(result),
                AgentRunEventType.CART_CHANGED);
    }
}
