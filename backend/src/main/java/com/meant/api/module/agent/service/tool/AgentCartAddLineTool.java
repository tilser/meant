package com.meant.api.module.agent.service.tool;

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
public class AgentCartAddLineTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "add_cart_line",
            "Add an exact server-issued offer to a current owned cart. Resolve references such as the second one, "
                    + "it, or add it again from the supplied conversation state. The offer is revalidated and must "
                    + "match the cart's merchant/provider route. Use only the exact cartable selectedOfferKey returned "
                    + "by select_product_variant in this run; never use its input anchor/default offer as a substitute. "
                    + "Every offer requires that current-run selection proof and is rejected without it.",
            """
                    {"type":"object","additionalProperties":false,"required":["cartId","offerKey"],"properties":{"cartId":{"type":"string","format":"uuid"},"offerKey":{"type":"string","minLength":1,"maxLength":200},"quantity":{"type":"integer","minimum":1,"maximum":1000}}}
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
        AgentCartToolArguments.AddLine arguments = support.arguments(
                argumentsJson, AgentCartToolArguments.AddLine.class);
        AgentCartResult result = AgentCartResult.success(List.of(support.addLine(context, arguments)));
        String resultJson = support.json(result);
        return new AgentToolExecutionResult(
                resultJson, "Added the selected offer to the cart.", support.artifacts(result),
                AgentRunEventType.CART_CHANGED);
    }
}
