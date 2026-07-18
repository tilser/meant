package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.ListRecentOrdersAgentToolInput;
import com.meant.api.module.order.service.OrderService;
import com.meant.api.module.order.service.dto.OrderListResult;
import com.meant.api.module.order.service.query.ListOrdersQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListRecentOrdersAgentToolTest {

    @Test
    void injectsTheContextUserAndDoesNotExposeUserIdInTheSchema() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        OrderService orderService = mock(OrderService.class);
        when(json.readArguments("{\"limit\":5}", ListRecentOrdersAgentToolInput.class))
                .thenReturn(new ListRecentOrdersAgentToolInput(5));
        when(json.write(any())).thenReturn("{\"orders\":[]}");
        when(orderService.list(any())).thenReturn(new OrderListResult(List.of(), 0, 5, false));
        ListRecentOrdersAgentTool tool = new ListRecentOrdersAgentTool(json, orderService);

        var result = tool.execute(
                new AgentToolExecutionContext(userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "orders"),
                "{\"limit\":5}");

        ArgumentCaptor<ListOrdersQuery> query = ArgumentCaptor.forClass(ListOrdersQuery.class);
        verify(orderService).list(query.capture());
        assertThat(query.getValue().userId()).isEqualTo(userId);
        assertThat(query.getValue().limit()).isEqualTo(5);
        assertThat(result.artifacts()).isEmpty();
        assertThat(tool.descriptor().inputSchemaJson())
                .doesNotContain("userId")
                .contains("\"additionalProperties\":false");
    }
}
