package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.GetOrderAgentToolInput;
import com.meant.api.module.order.service.OrderService;
import com.meant.api.module.order.service.dto.OrderResult;
import com.meant.api.module.order.service.query.GetOrderQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetOrderAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_order",
            "Load details for an order previously listed in this conversation.",
            """
            {"type":"object","properties":{"orderId":{"type":"string","format":"uuid"},"refresh":{"type":"boolean"}},"required":["orderId"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentProductReadReferenceService referenceService;
    private final OrderService orderService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        GetOrderAgentToolInput input = json.readArguments(argumentsJson, GetOrderAgentToolInput.class);
        referenceService.requireOrder(context, input.orderId());
        OrderResult order = orderService.get(new GetOrderQuery(
                input.orderId(), context.userId(), Boolean.TRUE.equals(input.refresh())));
        AgentArtifact artifact = new AgentArtifact(
                AgentArtifactType.ORDER, 1, "order:" + order.id(), order.displayId(),
                null, null, null, null, null, null, json.writeArtifact(order));
        return AgentToolExecutionResult.read(
                json.write(order), "Loaded order " + order.displayId() + ".", List.of(artifact));
    }
}
