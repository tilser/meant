package com.meant.api.module.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.order.repository.MerchantOrderRepository;
import com.meant.api.module.order.service.command.ReceiveShopifyOrderWebhookCommand;
import com.meant.api.module.order.service.query.GetOrderQuery;
import com.meant.api.module.order.service.query.ListOrdersQuery;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

@SpringBootTest
class ShopifyOrderWebhookServiceIT extends PostgresIntegrationTestSupport {

    private static final String SECRET = "test-shopify-webhook-secret";

    @Autowired
    private ShopifyOrderWebhookService webhookService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantOrderRepository orderRepository;

    @Test
    void verifiedWebhookUpsertsOrderStateAndLinesInPostgres() {
        UUID userId = UUID.randomUUID();
        User user = saveUser(userId, "ada-%s@example.com".formatted(userId));
        Merchant merchant = saveMerchant();
        String remoteOrderId = "gid://shopify/Order/" + UUID.randomUUID();

        receive(merchant, orderBody(remoteOrderId, "#1001", user.getEmail(), "paid", "unfulfilled", false));
        receive(merchant, orderBody(remoteOrderId, "#1001", user.getEmail(), "paid", "in_transit", false));
        receive(merchant, orderBody(remoteOrderId, "#1001", user.getEmail(), "paid", "fulfilled", false));
        receive(merchant, orderBody(remoteOrderId, "#1001", user.getEmail(), "paid", "unfulfilled", true));

        List<MerchantOrder> orders = orderRepository.findByUserIdOrderByPlacedAtDescCreatedAtDesc(
                userId,
                PageRequest.of(0, 10)
        ).getContent();
        assertThat(orders).hasSize(1);
        MerchantOrder order = orderRepository.findByIdAndUserId(orders.getFirst().getId(), userId).orElseThrow();
        assertThat(order.getState()).isEqualTo(OrderState.DELIVERED);
        assertThat(order.getRemoteOrderId()).isEqualTo(remoteOrderId);
        assertThat(order.getCustomerEmail()).isEqualTo(user.getEmail());
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getLines()).extracting("position")
                .containsExactlyInAnyOrder(0, 1);
        assertThat(orderService.list(new ListOrdersQuery(userId, 0, 20)).orders())
                .extracting("displayId")
                .containsExactly("#1001");
        assertThat(orderService.get(new GetOrderQuery(order.getId(), userId, false)).lines())
                .extracting("productTitle")
                .containsExactly("Diffuser", "Candle");
    }

    @Test
    void forgedWebhookIsRejectedBeforePersistence() {
        UUID userId = UUID.randomUUID();
        User user = saveUser(userId, "grace-%s@example.com".formatted(userId));
        Merchant merchant = saveMerchant();
        byte[] body = orderBody("gid://shopify/Order/" + UUID.randomUUID(), "#1002", user.getEmail(), "paid",
                "unfulfilled", false).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> webhookService.receive(new ReceiveShopifyOrderWebhookCommand(
                merchant.getDomain(),
                "orders/create",
                UUID.randomUUID().toString(),
                "forged",
                body
        )))
                .isInstanceOf(OrderException.class)
                .satisfies(exception -> assertThat(((OrderException) exception).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(orderRepository.findByUserIdOrderByPlacedAtDescCreatedAtDesc(userId, PageRequest.of(0, 10)))
                .isEmpty();
    }

    private void receive(Merchant merchant, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        webhookService.receive(new ReceiveShopifyOrderWebhookCommand(
                merchant.getDomain(),
                "orders/updated",
                UUID.randomUUID().toString(),
                hmac(bytes),
                bytes
        ));
    }

    private User saveUser(UUID id, String email) {
        Instant now = Instant.parse("2026-06-18T11:05:00Z");
        return userRepository.save(User.builder()
                .id(id)
                .email(email)
                .firstName("Ada")
                .surname("Lovelace")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Merchant saveMerchant() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-18T11:05:00Z");
        String domain = "merchant-%s.example".formatted(id);
        MerchantRaw merchantRaw = merchantRawRepository.save(MerchantRaw.builder()
                .id(UUID.randomUUID())
                .datasetRowIdx(Math.abs(id.hashCode()))
                .domain(domain)
                .status("OK")
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .httpStatus(200)
                .ucpVersion("1.0")
                .hasCheckout(true)
                .hasIdentityLinking(true)
                .hasCartManagement(true)
                .hasOrder(true)
                .hasPaymentToken(false)
                .capabilityCount(1)
                .transports("[]")
                .fetchedAt(now)
                .processed(true)
                .processingStatus("SUCCESS")
                .sourceHash("raw-hash-%s".formatted(id))
                .active(true)
                .lastSeenAt(now)
                .build());
        return merchantRepository.save(Merchant.builder()
                .id(id)
                .merchantRaw(merchantRaw)
                .domain(domain)
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .ucpVersion("1.0")
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .profileHash("hash-%s".formatted(id))
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private String orderBody(
            String orderId,
            String name,
            String email,
            String financialStatus,
            String fulfillmentStatus,
            boolean reverseLines
    ) {
        String candle = lineItem(
                "gid://shopify/LineItem/1",
                "Candle",
                2,
                "gid://shopify/Product/1",
                "gid://shopify/ProductVariant/1",
                "3x6",
                "14.95",
                "29.90"
        );
        String diffuser = lineItem(
                "gid://shopify/LineItem/2",
                "Diffuser",
                1,
                "gid://shopify/Product/2",
                "gid://shopify/ProductVariant/2",
                "Lavender",
                "9.99",
                "9.99"
        );
        String lineItems = reverseLines ? diffuser + ",\n" + candle : candle + ",\n" + diffuser;
        return """
                {
                  "id": "%s",
                  "name": "%s",
                  "order_number": "%s",
                  "financial_status": "%s",
                  "fulfillment_status": "%s",
                  "email": "%s",
                  "created_at": "2026-06-18T10:00:00Z",
                  "processed_at": "2026-06-18T10:05:00Z",
                  "updated_at": "2026-06-18T10:06:00Z",
                  "total_price": "39.89",
                  "subtotal_price": "39.89",
                  "currency": "USD",
                  "line_items": [
                %s
                  ]
                }
                """.formatted(
                orderId,
                name,
                name.replace("#", ""),
                financialStatus,
                fulfillmentStatus,
                email,
                lineItems.indent(4)
        );
    }

    private String lineItem(
            String id,
            String title,
            int quantity,
            String productId,
            String variantId,
            String variantTitle,
            String price,
            String totalPrice
    ) {
        return """
                {
                  "id": "%s",
                  "title": "%s",
                  "quantity": %d,
                  "product_id": "%s",
                  "variant_id": "%s",
                  "variant_title": "%s",
                  "price": "%s",
                  "total_price": "%s",
                  "currency": "USD"
                }
                """.formatted(id, title, quantity, productId, variantId, variantTitle, price, totalPrice).stripTrailing();
    }

    private String hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
