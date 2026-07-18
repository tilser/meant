package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import com.meant.api.module.checkout.exception.CheckoutAttributionException;
import com.meant.api.module.checkout.repository.CheckoutPurchaseAttributionRepository;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CheckoutPurchaseAttributionServiceTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final Instant PURCHASED_AT = Instant.parse("2026-07-18T10:15:30Z");

    @Mock private CartRepository cartRepository;
    @Mock private CheckoutPurchaseAttributionRepository attributionRepository;
    @Mock private UserInventoryService userInventoryService;
    private CheckoutPurchaseAttributionService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutPurchaseAttributionService(
                cartRepository,
                attributionRepository,
                userInventoryService,
                new ObjectMapper(),
                Clock.fixed(PURCHASED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void firstConfirmedStartReservesAttemptAndProjectsExactServerSnapshot() {
        Cart cart = currentCart(ATTEMPT_ID);
        when(cartRepository.findForCheckoutUpdate(CART_ID, USER_ID)).thenReturn(Optional.of(cart));
        whenReserve().thenReturn(1);

        assertThat(service.record(startCommand(ATTEMPT_ID, SESSION_ID))).isTrue();

        verify(attributionRepository).reserve(
                any(UUID.class),
                eq(USER_ID),
                eq(CART_ID),
                eq(ATTEMPT_ID),
                eq(CheckoutAttributionRail.EMBEDDED_CHECKOUT.name()),
                eq(CheckoutAttributionTrigger.CONFIRMED_ECP_START.name()),
                eq(SESSION_ID),
                eq(PURCHASED_AT),
                eq(PURCHASED_AT)
        );
        ArgumentCaptor<ImportPurchasedInventoryItemsCommand> captor =
                ArgumentCaptor.forClass(ImportPurchasedInventoryItemsCommand.class);
        verify(userInventoryService).importPurchasedItems(captor.capture());
        ImportPurchasedInventoryItemsCommand imported = captor.getValue();
        assertThat(imported.userId()).isEqualTo(USER_ID);
        assertThat(imported.checkoutAttemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(imported.purchasedAt()).isEqualTo(PURCHASED_AT);
        assertThat(imported.items()).singleElement().satisfies(item -> {
            assertThat(item.productKey()).isEqualTo("offer-shoe-size-42");
            assertThat(item.name()).isEqualTo("Trail Shoe");
            assertThat(item.brand()).isEqualTo("Meant Trail");
            assertThat(item.imageUrl()).isEqualTo("https://cdn.example/shoe.jpg");
            assertThat(item.productUrl()).isEqualTo("https://shop.example/products/trail-shoe");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.commerceReference()).satisfies(reference -> {
                assertThat(reference.provider()).isEqualTo("SHOPIFY");
                assertThat(reference.externalMerchantId()).isEqualTo("merchant-1");
                assertThat(reference.externalProductId()).isEqualTo("product-1");
                assertThat(reference.externalVariantId()).isEqualTo("variant-size-42");
                assertThat(reference.canonicalProductKey()).isEqualTo("canonical-shoe");
                assertThat(reference.selectedOptions()).singleElement().satisfies(option -> {
                    assertThat(option.group()).isEqualTo("variant-option");
                    assertThat(option.name()).isEqualTo("Size");
                    assertThat(option.value()).isEqualTo("42");
                });
            });
        });
    }

    @Test
    void duplicateStartAndCompletionFallbackForSameAttemptAreSuccessfulNoOps() {
        when(cartRepository.findForCheckoutUpdate(CART_ID, USER_ID))
                .thenReturn(Optional.of(currentCart(ATTEMPT_ID)));
        whenReserve().thenReturn(1, 0, 0);

        assertThat(service.record(startCommand(ATTEMPT_ID, SESSION_ID))).isTrue();
        assertThat(service.record(startCommand(ATTEMPT_ID, UUID.randomUUID()))).isFalse();
        assertThat(service.record(completionCommand(ATTEMPT_ID))).isFalse();

        verify(userInventoryService, times(1)).importPurchasedItems(any());
    }

    @Test
    void staleAttemptAndWrongUserAreRejectedBeforeReservation() {
        UUID staleAttemptId = UUID.randomUUID();
        when(cartRepository.findForCheckoutUpdate(CART_ID, USER_ID))
                .thenReturn(Optional.of(currentCart(ATTEMPT_ID)));

        assertThatThrownBy(() -> service.record(startCommand(staleAttemptId, SESSION_ID)))
                .isInstanceOf(CheckoutAttributionException.class)
                .hasMessage("Checkout attempt is stale");

        UUID otherUserId = UUID.randomUUID();
        when(cartRepository.findForCheckoutUpdate(CART_ID, otherUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.record(new RecordCheckoutOpenedCommand(
                otherUserId,
                CART_ID,
                ATTEMPT_ID,
                CheckoutAttributionRail.EMBEDDED_CHECKOUT,
                CheckoutAttributionTrigger.CONFIRMED_ECP_START,
                SESSION_ID
        )))
                .isInstanceOf(CheckoutAttributionException.class)
                .hasMessage("Checkout cart is missing or belongs to another user");

        verify(attributionRepository, never()).reserve(
                any(), any(), any(), any(), any(), any(), nullable(UUID.class), any(), any());
        verify(userInventoryService, never()).importPurchasedItems(any());
    }

    @Test
    void distinctCheckoutAttemptCanAttributeAnotherPurchase() {
        UUID nextAttemptId = UUID.randomUUID();
        when(cartRepository.findForCheckoutUpdate(CART_ID, USER_ID))
                .thenReturn(Optional.of(currentCart(ATTEMPT_ID)), Optional.of(currentCart(nextAttemptId)));
        whenReserve().thenReturn(1, 1);

        assertThat(service.record(startCommand(ATTEMPT_ID, SESSION_ID))).isTrue();
        assertThat(service.record(startCommand(nextAttemptId, UUID.randomUUID()))).isTrue();

        ArgumentCaptor<ImportPurchasedInventoryItemsCommand> captor =
                ArgumentCaptor.forClass(ImportPurchasedInventoryItemsCommand.class);
        verify(userInventoryService, times(2)).importPurchasedItems(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ImportPurchasedInventoryItemsCommand::checkoutAttemptId)
                .containsExactly(ATTEMPT_ID, nextAttemptId);
    }

    @Test
    void failedInventoryProjectionCanBeRetriedForTheSameAttempt() {
        when(cartRepository.findForCheckoutUpdate(CART_ID, USER_ID))
                .thenReturn(Optional.of(currentCart(ATTEMPT_ID)));
        whenReserve().thenReturn(1, 1);
        doThrow(new IllegalStateException("inventory write failed"))
                .doNothing()
                .when(userInventoryService).importPurchasedItems(any());

        assertThatThrownBy(() -> service.record(startCommand(ATTEMPT_ID, SESSION_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inventory write failed");
        assertThat(service.record(startCommand(ATTEMPT_ID, SESSION_ID))).isTrue();

        verify(attributionRepository, times(2)).reserve(
                any(), eq(USER_ID), eq(CART_ID), eq(ATTEMPT_ID), any(), any(), eq(SESSION_ID), any(), any());
        verify(userInventoryService, times(2)).importPurchasedItems(any());
    }

    private org.mockito.stubbing.OngoingStubbing<Integer> whenReserve() {
        return when(attributionRepository.reserve(
                any(UUID.class),
                eq(USER_ID),
                eq(CART_ID),
                any(UUID.class),
                any(String.class),
                any(String.class),
                nullable(UUID.class),
                eq(PURCHASED_AT),
                eq(PURCHASED_AT)
        ));
    }

    private RecordCheckoutOpenedCommand startCommand(UUID attemptId, UUID sessionId) {
        return new RecordCheckoutOpenedCommand(
                USER_ID,
                CART_ID,
                attemptId,
                CheckoutAttributionRail.EMBEDDED_CHECKOUT,
                CheckoutAttributionTrigger.CONFIRMED_ECP_START,
                sessionId
        );
    }

    private RecordCheckoutOpenedCommand completionCommand(UUID attemptId) {
        return new RecordCheckoutOpenedCommand(
                USER_ID,
                CART_ID,
                attemptId,
                CheckoutAttributionRail.NATIVE_CHECKOUT,
                CheckoutAttributionTrigger.VERIFIED_COMPLETION,
                null
        );
    }

    private Cart currentCart(UUID attemptId) {
        CartLine line = CartLine.builder()
                .remoteCartLineId("line-1")
                .productId("product-1")
                .productTitle("Trail Shoe")
                .productBrand("Meant Trail")
                .imageUrl("https://cdn.example/shoe.jpg")
                .productUrl("https://shop.example/products/trail-shoe")
                .productVariantId("variant-size-42")
                .quantity(2)
                .provider("SHOPIFY")
                .externalMerchantId("merchant-1")
                .externalProductId("product-1")
                .externalVariantId("variant-size-42")
                .offerKey("offer-shoe-size-42")
                .canonicalProductKey("canonical-shoe")
                .sourceType("PROVIDER_CATALOG")
                .sourceIdentity("SHOPIFY_GLOBAL_CATALOG")
                .selectedOptionsJson("[{\"group\":\"variant-option\",\"name\":\"Size\",\"value\":\"42\"}]")
                .rawLineResponse("{}")
                .createdAt(PURCHASED_AT)
                .updatedAt(PURCHASED_AT)
                .build();
        return Cart.builder()
                .id(CART_ID)
                .userId(USER_ID)
                .provider("SHOPIFY")
                .merchantDomain("shop.example")
                .endpoint("https://shop.example/api/ucp/mcp")
                .remoteCartId("cart-1")
                .remoteCartIdHash("cart-hash")
                .checkoutId("checkout-1")
                .checkoutAttemptId(attemptId)
                .checkoutAttemptCreatedAt(PURCHASED_AT.minusSeconds(60))
                .rawCartResponse("{}")
                .totalQuantity(2)
                .active(true)
                .createdAt(PURCHASED_AT.minusSeconds(120))
                .updatedAt(PURCHASED_AT)
                .refreshedAt(PURCHASED_AT)
                .lines(List.of(line))
                .build();
    }
}
