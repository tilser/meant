package com.meant.api.module.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.service.dto.OrderListResult;
import com.meant.api.module.order.service.query.ListOrdersQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

class OrderServiceTest {

    @Test
    void listRequestsBoundedPageAndMapsSummaryResults() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CapturingOrderPersistenceService persistenceService = new CapturingOrderPersistenceService();
        persistenceService.slice = new SliceImpl<>(
                List.of(order(userId)),
                PageRequest.of(2, 100),
                true
        );
        OrderService service = new OrderService(
                persistenceService,
                null,
                null,
                null,
                new OrderResultMapper()
        );

        OrderListResult result = service.list(new ListOrdersQuery(userId, 2, 500));

        assertThat(persistenceService.userId).isEqualTo(userId);
        assertThat(persistenceService.pageable.getPageNumber()).isEqualTo(2);
        assertThat(persistenceService.pageable.getPageSize()).isEqualTo(100);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.orders()).singleElement().satisfies(order -> {
            assertThat(order.displayId()).isEqualTo("#1001");
            assertThat(order.status()).isEqualTo("Processing");
            assertThat(order.totalQuantity()).isEqualTo(2);
        });
    }

    private static MerchantOrder order(UUID userId) {
        Instant now = Instant.parse("2026-06-18T10:05:00Z");
        return MerchantOrder.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000101"))
                .userId(userId)
                .merchantId(UUID.fromString("00000000-0000-0000-0000-000000000201"))
                .merchantDomain("merchant.example")
                .merchantName("Merchant")
                .endpoint("https://merchant.example/api/mcp")
                .remoteOrderId("gid://shopify/Order/1001")
                .remoteOrderIdHash("hash-1001")
                .orderName("#1001")
                .orderNumber("1001")
                .state(OrderState.PROCESSING)
                .rawOrderResponse("{}")
                .totalAmount("39.89")
                .subtotalAmount("39.89")
                .currency("USD")
                .totalQuantity(2)
                .placedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .build();
    }

    private static class CapturingOrderPersistenceService extends OrderPersistenceService {

        private UUID userId;
        private Pageable pageable;
        private Slice<MerchantOrder> slice;

        private CapturingOrderPersistenceService() {
            super(null, null);
        }

        @Override
        public Slice<MerchantOrder> listOrders(UUID userId, Pageable pageable) {
            this.userId = userId;
            this.pageable = pageable;
            return slice;
        }
    }
}
