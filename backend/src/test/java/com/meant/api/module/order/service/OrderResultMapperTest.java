package com.meant.api.module.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.entity.MerchantOrderLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderResultMapperTest {

    private final OrderResultMapper mapper = new OrderResultMapper();

    @Test
    void sanitizesProviderTextAndRejectsTransportUrlsInBuyerOrderProjection() {
        Instant now = Instant.parse("2026-07-23T18:30:00Z");
        MerchantOrderLine line = MerchantOrderLine.builder()
                .remoteOrderLineId("line-1")
                .position(0)
                .productId("product-1")
                .productTitle("Shoe from seller.myshopify.com")
                .productVariantId("variant-1")
                .variantTitle("Variant via https://seller.myshopify.com/api/ucp/mcp")
                .sku("SKU-1")
                .imageUrl("https://seller.myshopify.com/api/mcp/image.png")
                .productUrl("https://seller.myshopify.com/products/shoe")
                .quantity(1)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
        MerchantOrder order = MerchantOrder.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .merchantDomain("merchant.example")
                .merchantName("seller.myshopify.com")
                .endpoint("https://seller.myshopify.com/api/ucp/mcp")
                .remoteOrderId("gid://shopify/Order/1")
                .remoteOrderIdHash("hash")
                .orderName("#1001")
                .orderNumber("1001")
                .state(OrderState.PROCESSING)
                .orderStatusUrl("https://seller.myshopify.com/api/ucp/mcp/orders/1")
                .rawOrderResponse("{}")
                .totalQuantity(1)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .lines(List.of(line))
                .build();

        var result = mapper.from(order);

        assertThat(result.merchantDomain()).isEqualTo("merchant.example");
        assertThat(result.merchantName()).isEqualTo("merchant.example");
        assertThat(result.orderStatusUrl()).isNull();
        assertThat(result.lines()).singleElement().satisfies(orderLine -> {
            assertThat(orderLine.productTitle()).isEqualTo("Shoe from merchant.example");
            assertThat(orderLine.merchantName()).isEqualTo("merchant.example");
            assertThat(orderLine.variantTitle()).isEqualTo("Variant via merchant.example");
            assertThat(orderLine.imageUrl()).isNull();
            assertThat(orderLine.productUrl()).isNull();
        });
    }
}
