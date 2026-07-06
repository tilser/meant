package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.service.command.CreateCheckoutConsentCommand;
import com.meant.api.plugin.checkout.common.entity.BuyerConsent;
import com.meant.api.plugin.checkout.common.repository.BuyerConsentRepository;
import com.meant.api.plugin.checkout.common.service.BuyerConsentService;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartCheckoutConsentServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant NOW = Instant.parse("2026-06-16T11:05:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordConsentResolvesSelectedShippingMethodFromCheckout() {
        FakeBuyerConsentRepository repository = new FakeBuyerConsentRepository();
        CartCheckoutConsentService service = new CartCheckoutConsentService(
                new BuyerConsentService(repository.proxy(), objectMapper),
                objectMapper
        );
        Cart cart = cart();
        cart.replaceCheckoutSession(
                "checkout-1",
                "ready_for_complete",
                "https://merchant.example/checkout",
                "https://merchant.example/continue",
                """
                {
                  "checkout": {
                    "id": "checkout-1",
                    "status": "ready_for_complete",
                    "currency": "USD",
                    "total_amount": {"amount": "29.90", "currency": "USD"},
                    "shipping_method": {"name": "Standard Shipping"},
                    "line_items": [
                      {
                        "id": "line-1",
                        "product_variant_id": "variant-1",
                        "quantity": 1,
                        "total_amount": {"amount": "29.90", "currency": "USD"}
                      }
                    ]
                  }
                }
                """,
                NOW
        );

        service.recordConsent(cart, new CreateCheckoutConsentCommand(
                cart.getId(),
                USER_ID,
                "checkout-1",
                "card:test",
                "selected",
                "terms-hash"
        ));

        assertThat(repository.saved.getShippingMethod()).isEqualTo("Standard Shipping");
    }

    private Cart cart() {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .merchantId(MERCHANT_ID)
                .merchantDomain("merchant.example")
                .endpoint("https://merchant.example/mcp")
                .remoteCartId("remote-cart-1")
                .remoteCartIdHash("remote-cart-hash")
                .rawCartResponse("{}")
                .totalQuantity(1)
                .totalAmount("29.90")
                .subtotalAmount("29.90")
                .currency("USD")
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .refreshedAt(NOW)
                .build();
        cart.replaceLines(List.of(CartLine.builder()
                .remoteCartLineId("line-1")
                .productId("product-1")
                .productTitle("Candle")
                .productVariantId("variant-1")
                .quantity(1)
                .totalAmount("29.90")
                .subtotalAmount("29.90")
                .currency("USD")
                .rawLineResponse("{}")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build()));
        return cart;
    }

    private static final class FakeBuyerConsentRepository {

        private BuyerConsent saved;

        private BuyerConsentRepository proxy() {
            return (BuyerConsentRepository) Proxy.newProxyInstance(
                    BuyerConsentRepository.class.getClassLoader(),
                    new Class<?>[]{BuyerConsentRepository.class},
                    this::invoke
            );
        }

        private Object invoke(Object proxy, Method method, Object[] args) {
            if ("save".equals(method.getName())) {
                saved = (BuyerConsent) args[0];
                return saved;
            }
            if ("findByIdAndUserId".equals(method.getName())) {
                return Optional.empty();
            }
            if ("toString".equals(method.getName())) {
                return "FakeBuyerConsentRepository";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
