package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentOrderListResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.ListRecentOrdersAgentToolInput;
import com.meant.api.module.order.service.OrderService;
import com.meant.api.module.order.service.dto.OrderListResult;
import com.meant.api.module.order.service.query.ListOrdersQuery;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListRecentOrdersAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "list_recent_orders",
            "List the current user's recent merchant orders without refreshing remote state.",
            """
            {"type":"object","properties":{"limit":{"type":"integer","minimum":1,"maximum":20}},"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final OrderService orderService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        ListRecentOrdersAgentToolInput input = json.readArguments(argumentsJson, ListRecentOrdersAgentToolInput.class);
        int limit = input.limit() == null ? 10 : input.limit();
        if (limit < 1 || limit > 20) {
            throw AgentProductReadToolException.invalid("Limit must be between 1 and 20.");
        }
        OrderListResult result = orderService.list(new ListOrdersQuery(context.userId(), 0, limit));
        AgentOrderListResult payload = AgentOrderListResult.from(result);
        List<AgentArtifact> artifacts = IntStream.range(0, payload.orders().size())
                .mapToObj(index -> artifact(payload.orders().get(index), index + 1))
                .toList();
        return AgentToolExecutionResult.read(
                json.write(payload),
                "Loaded " + result.orders().size() + " recent order(s).",
                artifacts
        );
    }

    private AgentArtifact artifact(AgentOrderListResult.Order order, int ordinal) {
        return new AgentArtifact(
                AgentArtifactType.ORDER, ordinal, "order:" + order.id(), order.displayId(),
                null, null, null, null, null, null, json.writeArtifact(order));
    }
}
