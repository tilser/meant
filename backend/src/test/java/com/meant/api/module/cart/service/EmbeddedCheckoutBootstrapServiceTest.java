package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.constant.EmbeddedCheckoutBootstrapAction;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.dto.EmbeddedCheckoutConfiguration;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import com.meant.api.module.checkout.properties.EmbeddedCheckoutProperties;
import com.meant.api.module.checkout.service.EmbeddedCheckoutSessionStore;
import com.meant.api.module.checkout.service.dto.EmbeddedCheckoutSessionBinding;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddedCheckoutBootstrapServiceTest {
    @Mock private CartService cartService;
    @Mock private CartPersistenceService persistenceService;
    @Mock private EmbeddedCheckoutSessionStore sessionStore;
    private EmbeddedCheckoutBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddedCheckoutBootstrapService(cartService, persistenceService, sessionStore,
                new EmbeddedCheckoutOriginPolicy(new com.meant.api.common.properties.CorsProperties(
                        List.of("https://meant.com"))),
                new EmbeddedCheckoutProperties(Duration.ofMinutes(5), "2026-04-08"));
    }

    @Test
    void createsBoundSessionOnlyForPerCheckoutEmbeddedBinding() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                new EmbeddedCheckoutConfiguration("2026-04-08", List.of("payment.credential"), null)));
        when(sessionStore.create(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                UUID.randomUUID(), cart.getId(), "checkout-1", "https://meant.com", "2026-04-08",
                Instant.parse("2026-07-11T20:05:00Z")));

        var result = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(result.action()).isEqualTo(EmbeddedCheckoutBootstrapAction.EMBEDDED);
        assertThat(result.allowedDelegations()).isEmpty();
        assertThat(result.ecAuth()).isNull();
        assertThat(result.checkoutUrl()).isEqualTo("https://shop.example/checkout/1");
        assertThat(result.fallbackContinueUrl()).isEqualTo("https://shop.example/checkout/1");
        verify(sessionStore).create(any());
    }

    @Test
    void requiresEscalationUsesContinueUrlWithoutASeparateEmbeddedServiceAdvertisement() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(
                CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                null,
                "https://checkout.delegated.example/checkout/1"
        ));
        when(sessionStore.create(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                UUID.randomUUID(), cart.getId(), "checkout-1", "https://meant.com", "2026-04-08",
                Instant.parse("2026-07-11T20:05:00Z")));

        var result = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(result.action()).isEqualTo(EmbeddedCheckoutBootstrapAction.EMBEDDED);
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.delegated.example/checkout/1");
        verify(sessionStore).create(any());
    }

    @Test
    void completionIsNotTrustedUntilRemoteCheckoutIsCompleted() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        UUID sessionId = UUID.randomUUID();
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(sessionStore.requireActive(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                sessionId, cart.getId(), "checkout-1", "https://meant.com", "2026-04-08", Instant.MAX));
        when(cartService.checkout(any())).thenReturn(checkout(CheckoutNextAction.WAIT, null));

        assertThatThrownBy(() -> service.complete(cart.getId(), sessionId, userId, "https://meant.com"))
                .isInstanceOf(EmbeddedCheckoutException.class);
        verify(sessionStore, never()).complete(any());
    }

    private Cart cart(UUID userId) {
        return Cart.builder().id(UUID.randomUUID()).userId(userId).merchantDomain("shop.example")
                .provider("SHOPIFY").routingScopeKey("SHOPIFY:merchant:1")
                .endpoint("https://shop.example/api/ucp/mcp").remoteCartId("cart-1")
                .remoteCartIdHash("hash").checkoutId("checkout-1").active(true).totalQuantity(1)
                .rawCartResponse("{}").createdAt(Instant.now()).updatedAt(Instant.now()).refreshedAt(Instant.now())
                .build();
    }

    private CheckoutResult checkout(CheckoutNextAction action, EmbeddedCheckoutConfiguration configuration) {
        return checkout(action, configuration, "https://shop.example/checkout/1");
    }

    private CheckoutResult checkout(
            CheckoutNextAction action,
            EmbeddedCheckoutConfiguration configuration,
            String continueUrl
    ) {
        return new CheckoutResult(UUID.randomUUID(), "cart-1", "checkout-1", "requires_escalation",
                "https://shop.example/checkouts/embedded/1",
                continueUrl, "2026-04-08", 1000L, "USD", List.of(), action,
                action == CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT
                        ? CommerceExecutionRail.EMBEDDED_CHECKOUT : CommerceExecutionRail.NONE,
                List.of(), MerchantExecutionPolicy.unavailable(), configuration);
    }
}
