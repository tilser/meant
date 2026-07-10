package com.meant.api.module.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.entity.MerchantOrderLine;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class MerchantOrderRepositoryIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MerchantOrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void listOrdersReturnsPagedSummariesWithoutLoadingLines() {
        UUID userId = UUID.randomUUID();
        saveUser(userId);
        Merchant merchant = saveMerchant();
        saveOrder(userId, merchant, "remote-1", Instant.parse("2026-06-18T10:00:00Z"));
        saveOrder(userId, merchant, "remote-2", Instant.parse("2026-06-18T11:00:00Z"));
        saveOrder(userId, merchant, "remote-3", Instant.parse("2026-06-18T12:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        Slice<MerchantOrder> orders = orderRepository.findByUserIdOrderByPlacedAtDescCreatedAtDesc(
                userId,
                PageRequest.of(0, 2)
        );

        assertThat(orders.getContent())
                .extracting(MerchantOrder::getRemoteOrderId)
                .containsExactly("remote-3", "remote-2");
        assertThat(orders.hasNext()).isTrue();
        assertThat(Hibernate.isInitialized(orders.getContent().getFirst().getLines())).isFalse();
    }

    private User saveUser(UUID userId) {
        Instant now = Instant.parse("2026-06-18T12:30:00Z");
        return userRepository.save(User.builder()
                .id(userId)
                .email("order-summary-%s@example.com".formatted(userId))
                .firstName("Ada")
                .surname("Lovelace")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Merchant saveMerchant() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-18T12:30:00Z");
        String domain = "merchant-order-summary-%s.example".formatted(id);
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

    private void saveOrder(UUID userId, Merchant merchant, String remoteOrderId, Instant placedAt) {
        Instant now = Instant.parse("2026-06-18T12:30:00Z");
        MerchantOrder order = MerchantOrder.builder()
                .userId(userId)
                .merchantId(merchant.getId())
                .merchantDomain(merchant.getDomain())
                .merchantName(merchant.getName())
                .endpoint("https://merchant.example/api/mcp")
                .remoteOrderId(remoteOrderId)
                .remoteOrderIdHash("hash-" + remoteOrderId)
                .orderName("#" + remoteOrderId)
                .orderNumber(remoteOrderId)
                .state(OrderState.PROCESSING)
                .rawOrderResponse("{}")
                .totalAmount("39.89")
                .subtotalAmount("39.89")
                .currency("USD")
                .totalQuantity(1)
                .placedAt(placedAt)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .build();
        order.replaceLines(List.of(MerchantOrderLine.builder()
                .remoteOrderLineId("line-" + remoteOrderId)
                .position(0)
                .productTitle("Candle")
                .quantity(1)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build()));
        orderRepository.save(order);
    }
}
