package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.constant.EmbeddedCheckoutBootstrapAction;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.dto.EmbeddedCheckoutConfiguration;
import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import com.meant.api.module.checkout.exception.CheckoutAttributionException;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import com.meant.api.module.checkout.properties.EmbeddedCheckoutProperties;
import com.meant.api.module.checkout.service.CheckoutPurchaseAttributionService;
import com.meant.api.module.checkout.service.EmbeddedCheckoutSessionStore;
import com.meant.api.module.checkout.service.command.CreateEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddedCheckoutBootstrapServiceTest {
    @Mock private CartService cartService;
    @Mock private CartPersistenceService persistenceService;
    @Mock private EmbeddedCheckoutSessionStore sessionStore;
    @Mock private CheckoutPurchaseAttributionService attributionService;
    private EmbeddedCheckoutBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddedCheckoutBootstrapService(
                cartService, persistenceService, sessionStore, attributionService,
                new EmbeddedCheckoutOriginPolicy(new com.meant.api.common.properties.CorsProperties(
                        List.of("https://meant.com"))),
                new EmbeddedCheckoutProperties(Duration.ofMinutes(5), "2026-04-08"));
    }

    @Test
    void createsBoundSessionOnlyForPerCheckoutEmbeddedBinding() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(cart, CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                new EmbeddedCheckoutConfiguration("2026-04-08", List.of("payment.credential"), null)));
        when(sessionStore.create(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                UUID.randomUUID(), cart.getId(), "checkout-1", cart.getCheckoutAttemptId(),
                "https://meant.com", "2026-04-08", Instant.parse("2026-07-11T20:05:00Z")));

        var result = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(result.action()).isEqualTo(EmbeddedCheckoutBootstrapAction.EMBEDDED);
        assertThat(result.allowedDelegations()).isEmpty();
        assertThat(result.ecAuth()).isNull();
        assertThat(result.checkoutUrl()).isEqualTo("https://shop.example/checkout/1");
        assertThat(result.fallbackContinueUrl()).isEqualTo("https://shop.example/checkout/1");
        assertThat(result.checkoutAttemptId()).isEqualTo(cart.getCheckoutAttemptId());
        verify(sessionStore).create(any());
        verifyNoInteractions(attributionService);
    }

    @Test
    void repeatedEmbeddedBootstrapsCreateNewSessionsForTheSameCheckoutAttempt() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(cart, CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                new EmbeddedCheckoutConfiguration("2026-04-08", List.of(), null)));
        when(sessionStore.create(any())).thenAnswer(invocation -> {
            CreateEmbeddedCheckoutSessionCommand command = invocation.getArgument(0);
            return new EmbeddedCheckoutSessionBinding(
                    UUID.randomUUID(), command.cartId(), command.checkoutId(), command.checkoutAttemptId(),
                    command.allowedOrigin(), command.protocolVersion(), Instant.parse("2026-07-11T20:05:00Z"));
        });

        var first = service.bootstrap(cart.getId(), userId, "https://meant.com");
        var second = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(first.checkoutAttemptId()).isEqualTo(cart.getCheckoutAttemptId());
        assertThat(second.checkoutAttemptId()).isEqualTo(cart.getCheckoutAttemptId());
        ArgumentCaptor<CreateEmbeddedCheckoutSessionCommand> commands =
                ArgumentCaptor.forClass(CreateEmbeddedCheckoutSessionCommand.class);
        verify(sessionStore, times(2)).create(commands.capture());
        assertThat(commands.getAllValues()).extracting(CreateEmbeddedCheckoutSessionCommand::checkoutAttemptId)
                .containsOnly(cart.getCheckoutAttemptId());
        verifyNoInteractions(attributionService);
    }

    @Test
    void requiresEscalationUsesContinueUrlWithoutASeparateEmbeddedServiceAdvertisement() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(cart,
                CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                null,
                "https://checkout.delegated.example/checkout/1"
        ));
        when(sessionStore.create(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                UUID.randomUUID(), cart.getId(), "checkout-1", cart.getCheckoutAttemptId(),
                "https://meant.com", "2026-04-08", Instant.parse("2026-07-11T20:05:00Z")));

        var result = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(result.action()).isEqualTo(EmbeddedCheckoutBootstrapAction.EMBEDDED);
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.delegated.example/checkout/1");
        verify(sessionStore).create(any());
        verifyNoInteractions(attributionService);
    }

    @Test
    void blankCheckoutRoutesAreUnavailableAndDoNotCreateSession() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(cart,
                CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                null,
                " ",
                " "
        ));

        var result = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(result.action()).isEqualTo(EmbeddedCheckoutBootstrapAction.UNAVAILABLE);
        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.fallbackContinueUrl()).isNull();
        verify(sessionStore, never()).create(any());
        verifyNoInteractions(attributionService);
    }

    @Test
    void waitingBootstrapDoesNotAttributeOrCreateAnEmbeddedSession() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(cartService.checkout(any())).thenReturn(checkout(cart, CheckoutNextAction.WAIT, null));

        var result = service.bootstrap(cart.getId(), userId, "https://meant.com");

        assertThat(result.action()).isEqualTo(EmbeddedCheckoutBootstrapAction.WAIT);
        assertThat(result.checkoutAttemptId()).isEqualTo(cart.getCheckoutAttemptId());
        verify(sessionStore, never()).create(any());
        verifyNoInteractions(attributionService);
    }

    @Test
    void completionIsNotTrustedUntilRemoteCheckoutIsCompleted() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        UUID sessionId = UUID.randomUUID();
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(sessionStore.requireActive(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                sessionId, cart.getId(), "checkout-1", cart.getCheckoutAttemptId(),
                "https://meant.com", "2026-04-08", Instant.MAX));
        when(cartService.checkout(any())).thenReturn(checkout(cart, CheckoutNextAction.WAIT, null));

        assertThatThrownBy(() -> service.complete(cart.getId(), sessionId, userId, "https://meant.com"))
                .isInstanceOf(EmbeddedCheckoutException.class);
        verify(sessionStore, never()).complete(any());
        verifyNoInteractions(attributionService);
    }

    @Test
    void attributionFailureDoesNotMaskProviderVerifiedEmbeddedCompletion() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        UUID sessionId = UUID.randomUUID();
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(sessionStore.requireActive(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                sessionId, cart.getId(), "checkout-1", cart.getCheckoutAttemptId(),
                "https://meant.com", "2026-04-08", Instant.MAX));
        when(cartService.checkout(any())).thenReturn(checkout(cart, CheckoutNextAction.DONE, null));
        when(attributionService.record(any()))
                .thenThrow(CheckoutAttributionException.conflict("Checkout attempt became stale"));

        CheckoutResult result = service.complete(cart.getId(), sessionId, userId, "https://meant.com");

        assertThat(result.status()).isEqualTo("completed");
        verify(attributionService).record(any());
        verify(sessionStore).complete(any());
    }

    @Test
    void repeatedOpenedAcknowledgementsUseTheSameAttemptAndRemainIdempotentAtTheAttributionBoundary() {
        UUID userId = UUID.randomUUID();
        Cart cart = cart(userId);
        UUID sessionId = UUID.randomUUID();
        when(persistenceService.findCart(cart.getId(), userId)).thenReturn(cart);
        when(sessionStore.acknowledgeOpened(any())).thenReturn(new EmbeddedCheckoutSessionBinding(
                sessionId, cart.getId(), "checkout-1", cart.getCheckoutAttemptId(),
                "https://meant.com", "2026-04-08", Instant.MAX));

        service.opened(cart.getId(), sessionId, userId, "https://meant.com");
        service.opened(cart.getId(), sessionId, userId, "https://meant.com");

        verify(sessionStore, times(2)).acknowledgeOpened(any());
        ArgumentCaptor<RecordCheckoutOpenedCommand> commands =
                ArgumentCaptor.forClass(RecordCheckoutOpenedCommand.class);
        verify(attributionService, times(2)).record(commands.capture());
        assertThat(commands.getAllValues()).allSatisfy(command -> {
            assertThat(command.userId()).isEqualTo(userId);
            assertThat(command.cartId()).isEqualTo(cart.getId());
            assertThat(command.checkoutAttemptId()).isEqualTo(cart.getCheckoutAttemptId());
            assertThat(command.rail()).isEqualTo(CheckoutAttributionRail.EMBEDDED_CHECKOUT);
            assertThat(command.trigger()).isEqualTo(CheckoutAttributionTrigger.CONFIRMED_ECP_START);
            assertThat(command.embeddedSessionId()).isEqualTo(sessionId);
        });
    }

    private Cart cart(UUID userId) {
        return Cart.builder().id(UUID.randomUUID()).userId(userId).merchantDomain("shop.example")
                .provider("SHOPIFY").routingScopeKey("SHOPIFY:merchant:1")
                .endpoint("https://shop.example/api/ucp/mcp").remoteCartId("cart-1")
                .remoteCartIdHash("hash").checkoutId("checkout-1").checkoutAttemptId(UUID.randomUUID())
                .checkoutAttemptCreatedAt(Instant.parse("2026-07-11T20:00:00Z"))
                .active(true).totalQuantity(1)
                .rawCartResponse("{}").createdAt(Instant.now()).updatedAt(Instant.now()).refreshedAt(Instant.now())
                .build();
    }

    private CheckoutResult checkout(
            Cart cart,
            CheckoutNextAction action,
            EmbeddedCheckoutConfiguration configuration
    ) {
        return checkout(cart, action, configuration, "https://shop.example/checkout/1");
    }

    private CheckoutResult checkout(
            Cart cart,
            CheckoutNextAction action,
            EmbeddedCheckoutConfiguration configuration,
            String continueUrl
    ) {
        return checkout(cart, action, configuration, continueUrl, "https://shop.example/checkouts/embedded/1");
    }

    private CheckoutResult checkout(
            Cart cart,
            CheckoutNextAction action,
            EmbeddedCheckoutConfiguration configuration,
            String continueUrl,
            String checkoutUrl
    ) {
        return new CheckoutResult(cart.getId(), "cart-1", "checkout-1", cart.getCheckoutAttemptId(),
                action == CheckoutNextAction.DONE ? "completed" : "requires_escalation",
                checkoutUrl,
                continueUrl, "2026-04-08", 1000L, "USD", List.of(), action,
                action == CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT
                        ? CommerceExecutionRail.EMBEDDED_CHECKOUT : CommerceExecutionRail.NONE,
                List.of(), MerchantExecutionPolicy.unavailable(), configuration);
    }
}
