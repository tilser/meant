package com.meant.api.module.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.cart.constant.CartSnapshotPurpose;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.CartPersistenceService;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class CheckoutPersistenceConcurrencyIT extends PostgresIntegrationTestSupport {
    @Autowired
    private CartRepository repository;

    @Autowired
    private CartPersistenceService persistence;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentDifferentCheckoutIdsCannotSubstituteTheLogicalSession() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(userId).email("checkout-concurrency-" + userId + "@example.test")
                .createdAt(now).updatedAt(now).build());
        Cart cart = repository.saveAndFlush(Cart.builder()
                .id(UUID.randomUUID()).userId(userId).provider("SHOPIFY")
                .merchantDomain("shop.test").externalMerchantId("shop-1")
                .routingScopeKey("SHOPIFY:merchant:shop-1:domain:shop.test")
                .endpoint("https://shop.test/api/ucp/mcp").remoteCartId("remote-cart-" + UUID.randomUUID())
                .remoteCartIdHash(UUID.randomUUID().toString()).rawCartResponse("{}")
                .totalQuantity(1).active(true).createdAt(Instant.now()).updatedAt(Instant.now())
                .refreshedAt(Instant.now()).build());
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> save(start, cart.getId(), userId, "checkout-a"));
            Future<Object> second = executor.submit(() -> save(start, cart.getId(), userId, "checkout-b"));
            start.countDown();
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(java.util.List.of(firstResult, secondResult))
                    .as("concurrent results: %s / %s", firstResult, secondResult)
                    .filteredOn(Cart.class::isInstance).hasSize(1);
            assertThat(java.util.List.of(firstResult, secondResult))
                    .filteredOn(CartException.class::isInstance).hasSize(1);
            Cart persisted = repository.findById(cart.getId()).orElseThrow();
            assertThat(persisted.getCheckoutId()).isIn("checkout-a", "checkout-b");
            assertThat(persisted.getCheckoutLifecycleState()).isEqualTo("INCOMPLETE");
            assertThat(persisted.getRawCheckoutResponse()).isNull();
        } finally {
            repository.deleteById(cart.getId());
            userRepository.deleteById(userId);
        }
    }

    @Test
    void checkoutResponseStartedBeforeCartMutationCannotRebindTheMutatedCart() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(userId).email("checkout-stale-" + userId + "@example.test")
                .createdAt(now).updatedAt(now).build());
        Cart cart = repository.saveAndFlush(Cart.builder()
                .id(UUID.randomUUID()).userId(userId).provider("SHOPIFY")
                .merchantDomain("shop.test").externalMerchantId("shop-1")
                .routingScopeKey("SHOPIFY:merchant:shop-1:domain:shop.test")
                .endpoint("https://shop.test/api/ucp/mcp").remoteCartId("remote-cart-" + UUID.randomUUID())
                .remoteCartIdHash(UUID.randomUUID().toString()).rawCartResponse("{}")
                .totalQuantity(1).active(true).createdAt(now).updatedAt(now).refreshedAt(now).build());
        long checkoutRequestGeneration = cart.getCheckoutGeneration();
        Instant mutatedAt = now.plusSeconds(1);
        cart.replaceSnapshot(
                cart.getEndpoint(), cart.getRemoteCartId(), cart.getRemoteCartIdHash(), null, null, null,
                "{}", 2, null, null, null, null, null, null, mutatedAt, CartSnapshotPurpose.CART_MUTATION);
        repository.saveAndFlush(cart);

        String json = "{\"ucp\":{\"version\":\"2026-04-08\"},\"checkout\":{\"id\":\"checkout-stale\","
                + "\"cart_id\":\"" + cart.getRemoteCartId() + "\",\"status\":\"incomplete\"}}";
        UcpCheckoutResponse response = objectMapper.readValue(json, UcpCheckoutResponse.class);

        assertThatThrownBy(() -> persistence.saveCheckoutHandoff(
                cart.getId(), userId, checkoutRequestGeneration,
                new UcpCheckoutToolResult("https://shop.test/api/ucp/mcp", json, response)))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("Cart changed while the remote checkout operation was in flight");

        Cart persisted = repository.findById(cart.getId()).orElseThrow();
        assertThat(persisted.getCheckoutGeneration()).isEqualTo(checkoutRequestGeneration + 1);
        assertThat(persisted.getCheckoutId()).isNull();
        assertThat(persisted.getCheckoutAttemptId()).isNull();
    }

    private Object save(CountDownLatch start, UUID cartId, UUID userId, String checkoutId) {
        try {
            start.await(5, TimeUnit.SECONDS);
            Cart cart = repository.findById(cartId).orElseThrow();
            String json = "{\"ucp\":{\"version\":\"2026-04-08\"},\"checkout\":{\"id\":\""
                    + checkoutId + "\",\"cart_id\":\"" + cart.getRemoteCartId()
                    + "\",\"status\":\"incomplete\"}}";
            UcpCheckoutResponse response = objectMapper.readValue(json, UcpCheckoutResponse.class);
            return persistence.saveCheckoutHandoff(
                    cartId, userId, cart.getCheckoutGeneration(), new UcpCheckoutToolResult(
                    "https://shop.test/api/ucp/mcp", json, response));
        } catch (Exception exception) {
            return exception;
        }
    }
}
