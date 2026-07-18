package com.meant.api.module.cart.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CartSnapshotPurpose;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartCheckoutAttemptIdentityTest {

    @Test
    void checkoutAttemptIsMintedOncePreservedAcrossRefreshAndReplacedAfterCartMutation() {
        Instant createdAt = Instant.parse("2026-07-11T20:00:00Z");
        Instant establishedAt = Instant.parse("2026-07-11T20:01:00Z");
        Instant refreshedAt = Instant.parse("2026-07-11T20:02:00Z");
        Instant mutatedAt = Instant.parse("2026-07-11T20:03:00Z");
        Instant restartedAt = Instant.parse("2026-07-11T20:04:00Z");
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .endpoint("https://shop.example/api/ucp/mcp")
                .remoteCartId("cart-1")
                .remoteCartIdHash("hash")
                .rawCartResponse("{}")
                .totalQuantity(1)
                .active(true)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .refreshedAt(createdAt)
                .build();

        cart.replaceCheckoutSession(
                "checkout-1", "incomplete", "https://shop.example/checkout/1",
                "https://shop.example/continue/1", null, "2026-04-08", "INCOMPLETE", establishedAt);
        UUID firstAttemptId = cart.getCheckoutAttemptId();
        long initialGeneration = cart.getCheckoutGeneration();

        assertThat(firstAttemptId).isNotNull();
        assertThat(cart.getCheckoutAttemptCreatedAt()).isEqualTo(establishedAt);
        assertThat(initialGeneration).isZero();

        cart.replaceCheckoutSession(
                "checkout-1", "processing", "https://shop.example/checkout/1",
                "https://shop.example/continue/1", null, "2026-04-08", "PROCESSING", refreshedAt);
        cart.replaceSnapshot(
                cart.getEndpoint(), cart.getRemoteCartId(), cart.getRemoteCartIdHash(),
                "https://ignored.example/checkout", "https://ignored.example/continue", null, "{}", 1,
                null, null, null, null, null, null, refreshedAt, CartSnapshotPurpose.READ_REFRESH);

        assertThat(cart.getCheckoutAttemptId()).isEqualTo(firstAttemptId);
        assertThat(cart.getCheckoutAttemptCreatedAt()).isEqualTo(establishedAt);
        assertThat(cart.getCheckoutGeneration()).isEqualTo(initialGeneration);

        cart.replaceSnapshot(
                cart.getEndpoint(), cart.getRemoteCartId(), cart.getRemoteCartIdHash(),
                null, null, null, "{}", 2, null, null, null, null, null, null, mutatedAt,
                CartSnapshotPurpose.CART_MUTATION);

        assertThat(cart.getCheckoutId()).isNull();
        assertThat(cart.getCheckoutAttemptId()).isNull();
        assertThat(cart.getCheckoutAttemptCreatedAt()).isNull();
        assertThat(cart.getCheckoutGeneration()).isEqualTo(initialGeneration + 1);

        cart.replaceCheckoutSession(
                "checkout-2", "incomplete", "https://shop.example/checkout/2",
                "https://shop.example/continue/2", null, "2026-04-08", "INCOMPLETE", restartedAt);

        assertThat(cart.getCheckoutAttemptId()).isNotNull().isNotEqualTo(firstAttemptId);
        assertThat(cart.getCheckoutAttemptCreatedAt()).isEqualTo(restartedAt);
    }
}
