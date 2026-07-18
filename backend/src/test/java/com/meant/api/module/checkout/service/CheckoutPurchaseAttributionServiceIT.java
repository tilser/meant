package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import com.meant.api.module.checkout.repository.CheckoutPurchaseAttributionRepository;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CheckoutPurchaseAttributionServiceIT extends PostgresIntegrationTestSupport {

    @Autowired private CheckoutPurchaseAttributionService service;
    @Autowired private CheckoutPurchaseAttributionRepository attributionRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserInventoryItemRepository inventoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateCallbacksCommitOneLedgerAndOneInventoryProjection() throws Exception {
        TestCheckout checkout = persistCheckout();
        RecordCheckoutOpenedCommand command = command(checkout, null);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> callbacks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            callbacks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.record(command);
            });
        }

        List<Boolean> results;
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> futures = callbacks.stream().map(executor::submit).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = futures.stream().map(this::result).toList();
        }

        assertThat(results).containsOnlyOnce(true);
        assertThat(results).filteredOn(Boolean.FALSE::equals).hasSize(7);
        assertThat(attributionRepository.existsByUserIdAndCheckoutAttemptId(
                checkout.userId(), checkout.attemptId())).isTrue();
        assertThat(inventoryRepository.findByUserIdAndSourceAndSourceProductKey(
                checkout.userId(), UserInventorySource.MEANT_PURCHASE, "offer-shoe-size-42"))
                .get()
                .satisfies(item -> {
                    assertThat(item.getQuantity()).isEqualTo(2);
                    assertThat(item.getExternalVariantId()).isEqualTo("variant-size-42");
                    assertThat(item.getSourceCheckoutAttemptId()).isEqualTo(checkout.attemptId());
                });
    }

    @Test
    void inventoryFailureRollsBackLedgerAndRetryCommitsBoth() {
        TestCheckout checkout = persistCheckout();
        RecordCheckoutOpenedCommand command = command(checkout, null);
        fillInventoryQuota(checkout.userId());

        assertThatThrownBy(() -> service.record(command))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("quota exceeded");
        assertThat(attributionRepository.existsByUserIdAndCheckoutAttemptId(
                checkout.userId(), checkout.attemptId())).isFalse();
        assertThat(inventoryRepository.findByUserIdAndSourceAndSourceProductKey(
                checkout.userId(), UserInventorySource.MEANT_PURCHASE, "offer-shoe-size-42")).isEmpty();

        jdbcTemplate.update(
                "DELETE FROM user_inventory_items WHERE user_id = ? AND source_product_key = 'quota-500'",
                checkout.userId());
        assertThat(service.record(command)).isTrue();

        assertThat(attributionRepository.existsByUserIdAndCheckoutAttemptId(
                checkout.userId(), checkout.attemptId())).isTrue();
        assertThat(inventoryRepository.findByUserIdAndSourceAndSourceProductKey(
                checkout.userId(), UserInventorySource.MEANT_PURCHASE, "offer-shoe-size-42")).isPresent();
    }

    private void fillInventoryQuota(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO user_inventory_items (
                    id, user_id, source, source_product_key, name, category, quantity, attributes,
                    consumable, restock_enabled, created_at, updated_at
                )
                SELECT gen_random_uuid(), ?, 'MANUAL', 'quota-' || value, 'Quota item', 'OTHER', 1, '[]',
                       false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM generate_series(1, 500) AS value
                """, userId);
    }

    private TestCheckout persistCheckout() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(userId)
                .email("checkout-attribution-" + userId + "@example.test")
                .createdAt(now)
                .updatedAt(now)
                .build());
        Cart cart = Cart.builder()
                .id(cartId)
                .userId(userId)
                .provider("SHOPIFY")
                .merchantDomain("shop.example")
                .externalMerchantId("merchant-1")
                .routingScopeKey("SHOPIFY:merchant:merchant-1:domain:shop.example")
                .endpoint("https://shop.example/api/ucp/mcp")
                .remoteCartId("cart-" + cartId)
                .remoteCartIdHash(UUID.randomUUID().toString())
                .checkoutId("checkout-" + cartId)
                .checkoutAttemptId(attemptId)
                .checkoutAttemptCreatedAt(now)
                .checkoutLifecycleState("INCOMPLETE")
                .checkoutSynchronizedAt(now)
                .rawCartResponse("{}")
                .totalQuantity(2)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .build();
        cart.replaceLines(List.of(CartLine.builder()
                .remoteCartLineId("line-1")
                .productId("product-1")
                .productTitle("Trail Shoe")
                .productVariantId("variant-size-42")
                .quantity(2)
                .provider("SHOPIFY")
                .externalMerchantId("merchant-1")
                .externalProductId("product-1")
                .externalVariantId("variant-size-42")
                .offerProductId("product-1")
                .offerVariantId("variant-size-42")
                .offerKey("offer-shoe-size-42")
                .canonicalProductKey("canonical-shoe")
                .sourceType("PROVIDER_CATALOG")
                .sourceIdentity("SHOPIFY_GLOBAL_CATALOG")
                .selectedOptionsJson("[{\"group\":\"variant-option\",\"name\":\"Size\",\"value\":\"42\"}]")
                .componentsJson("[]")
                .selectedAt(now)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build()));
        cartRepository.saveAndFlush(cart);
        return new TestCheckout(userId, cartId, attemptId);
    }

    private RecordCheckoutOpenedCommand command(TestCheckout checkout, UUID sessionId) {
        return new RecordCheckoutOpenedCommand(
                checkout.userId(),
                checkout.cartId(),
                checkout.attemptId(),
                CheckoutAttributionRail.EMBEDDED_CHECKOUT,
                CheckoutAttributionTrigger.CONFIRMED_ECP_START,
                sessionId
        );
    }

    private boolean result(Future<Boolean> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private record TestCheckout(UUID userId, UUID cartId, UUID attemptId) {
    }
}
