package com.meant.api.module.order.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.controller.response.OrderListResponse;
import com.meant.api.module.order.service.OrderService;
import com.meant.api.module.order.service.dto.OrderListResult;
import com.meant.api.module.order.service.dto.OrderSummaryResult;
import com.meant.api.module.order.service.query.ListOrdersQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OrderControllerTest {

    private final CapturingOrderService orderService = new CapturingOrderService();
    private final OrderController controller = new OrderController(orderService);

    @Test
    void listClampsPaginationAndReturnsOrderSummaries() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        OrderListResponse response = controller.list(jwt(userId), -5, 999);

        assertThat(orderService.query).isEqualTo(new ListOrdersQuery(userId, 0, 100));
        assertThat(response.page()).isZero();
        assertThat(response.limit()).isEqualTo(100);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.orders()).singleElement().satisfies(order -> {
            assertThat(order.displayId()).isEqualTo("#1001");
            assertThat(order.state()).isEqualTo("PROCESSING");
            assertThat(order.totalQuantity()).isEqualTo(2);
        });
    }

    @Test
    void listUsesDefaultPaginationWhenParametersAreMissing() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        controller.list(jwt(userId), null, null);

        assertThat(orderService.query).isEqualTo(new ListOrdersQuery(userId, 0, 20));
    }

    private Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "ada@example.com")
                .build();
    }

    private static class CapturingOrderService extends OrderService {

        private ListOrdersQuery query;

        private CapturingOrderService() {
            super(null, null, null, null, null);
        }

        @Override
        public OrderListResult list(ListOrdersQuery query) {
            this.query = query;
            return new OrderListResult(
                    List.of(new OrderSummaryResult(
                            UUID.fromString("00000000-0000-0000-0000-000000000101"),
                            UUID.fromString("00000000-0000-0000-0000-000000000201"),
                            "merchant.example",
                            "Merchant",
                            "gid://shopify/Order/1001",
                            "#1001",
                            "1001",
                            OrderState.PROCESSING,
                            "Confirmed",
                            "Confirmed: the merchant is preparing this order.",
                            "2026-06-18",
                            "39.89",
                            "39.89",
                            "USD",
                            2,
                            "https://merchant.example/orders/1001",
                            Instant.parse("2026-06-18T10:05:00Z"),
                            Instant.parse("2026-06-18T10:06:00Z")
                    )),
                    query.page(),
                    query.limit(),
                    true
            );
        }
    }
}
