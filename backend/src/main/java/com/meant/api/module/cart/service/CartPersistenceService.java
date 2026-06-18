package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.cart.service.dto.CartToolResponse;
import com.meant.api.module.cart.service.dto.CartToolResult;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CartPersistenceService {

    private final CartRepository cartRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Cart findCart(UUID cartId, UUID userId) {
        Cart cart = cartRepository.findWithLinesByIdAndUserId(cartId, userId)
                .orElseThrow(() -> CartException.notFound("Cart not found: " + cartId));
        cart.getAppliedCodes().size();
        return cart;
    }

    @Transactional
    public Cart saveSnapshot(UUID cartId, UUID userId, MerchantCartProvider provider, CartToolResult result) {
        CartToolResponse.Cart remoteCart = result.response().cart();
        Instant now = Instant.now();
        Cart cart = cartId == null
                ? Cart.builder()
                        .userId(userId)
                        .merchantId(provider.merchantId())
                        .merchantDomain(provider.domain())
                        .createdAt(now)
                        .build()
                : findCart(cartId, userId);
        cart.assignProvider(provider.merchantId(), provider.domain());
        String remoteCartId = required(remoteCart.id(), "Remote cart id is required");
        CartToolResponse.Money totalAmount = remoteCart.cost() == null ? null : remoteCart.cost().totalAmount();
        CartToolResponse.Money subtotalAmount = remoteCart.cost() == null ? null : remoteCart.cost().subtotalAmount();
        String currency = currency(totalAmount, subtotalAmount);
        cart.replaceSnapshot(
                result.endpoint(),
                remoteCartId,
                hash(remoteCartId),
                remoteCart.checkoutUrl(),
                result.response().instructions(),
                result.rawResponse(),
                remoteCart.totalQuantity() == null ? totalQuantity(remoteCart.lines()) : remoteCart.totalQuantity(),
                amount(totalAmount),
                amount(subtotalAmount),
                currency,
                remoteCart.createdAt(),
                remoteCart.updatedAt(),
                now
        );
        cart.replaceLines(safeNonNullList(remoteCart.lines()).stream()
                .map(line -> toCartLine(line, now))
                .toList());
        cart.replaceAppliedCodes(toAppliedCodes(remoteCart, currency));
        return cartRepository.save(cart);
    }

    private CartLine toCartLine(CartToolResponse.Line line, Instant now) {
        CartToolResponse.Merchandise merchandise = line.merchandise();
        CartToolResponse.Product product = merchandise == null ? null : merchandise.product();
        CartToolResponse.Money totalAmount = line.cost() == null ? null : line.cost().totalAmount();
        CartToolResponse.Money subtotalAmount = line.cost() == null ? null : line.cost().subtotalAmount();
        return CartLine.builder()
                .remoteCartLineId(required(line.id(), "Remote cart line id is required"))
                .productId(product == null ? null : product.id())
                .productTitle(product == null ? null : product.title())
                .productVariantId(required(merchandise == null ? null : merchandise.id(), "Product variant id is required"))
                .variantTitle(merchandise == null ? null : merchandise.title())
                .quantity(line.quantity() == null ? 0 : line.quantity())
                .totalAmount(amount(totalAmount))
                .subtotalAmount(amount(subtotalAmount))
                .currency(currency(totalAmount, subtotalAmount))
                .rawLineResponse(toJson(line))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private List<CartAppliedCode> toAppliedCodes(CartToolResponse.Cart remoteCart, String cartCurrency) {
        List<AppliedCodeValue> values = new ArrayList<>();
        addAppliedCodeValues(values, CartAppliedCodeType.DISCOUNT, remoteCart.discountCodes(), cartCurrency);
        addAppliedCodeValues(values, CartAppliedCodeType.DISCOUNT, remoteCart.appliedDiscounts(), cartCurrency);
        addAppliedCodeValues(values, CartAppliedCodeType.DISCOUNT, remoteCart.discountAllocations(), cartCurrency);
        addAppliedCodeValues(values, CartAppliedCodeType.GIFT_CARD, remoteCart.giftCardCodes(), cartCurrency);
        addAppliedCodeValues(values, CartAppliedCodeType.GIFT_CARD, remoteCart.appliedGiftCards(), cartCurrency);

        List<CartAppliedCode> appliedCodes = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            AppliedCodeValue value = values.get(index);
            appliedCodes.add(CartAppliedCode.builder()
                    .type(value.type())
                    .code(value.code())
                    .label(value.label())
                    .applicable(value.applicable())
                    .amount(value.amount())
                    .currency(value.currency())
                    .displayOrder(index)
                    .build());
        }
        return appliedCodes;
    }

    private void addAppliedCodeValues(
            List<AppliedCodeValue> values,
            CartAppliedCodeType type,
            List<CartToolResponse.AppliedCode> appliedCodes,
            String cartCurrency
    ) {
        for (CartToolResponse.AppliedCode appliedCode : safeNonNullList(appliedCodes)) {
            AppliedCodeValue value = new AppliedCodeValue(
                    type,
                    blankToNull(appliedCode.code()),
                    blankToNull(appliedCode.label()),
                    appliedCode.applicable(),
                    amount(appliedCode.amount()),
                    currency(appliedCode.amount(), null, cartCurrency)
            );
            if (!value.hasDisplayValue()) {
                continue;
            }

            int existingIndex = indexOf(values, value);
            if (existingIndex >= 0) {
                values.set(existingIndex, values.get(existingIndex).merge(value));
            } else {
                values.add(value);
            }
        }
    }

    private int indexOf(List<AppliedCodeValue> values, AppliedCodeValue value) {
        for (int index = 0; index < values.size(); index++) {
            AppliedCodeValue existing = values.get(index);
            if (existing.sameCode(value)) {
                return index;
            }
        }
        return -1;
    }

    private int totalQuantity(List<CartToolResponse.Line> lines) {
        return safeNonNullList(lines).stream()
                .map(CartToolResponse.Line::quantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String amount(CartToolResponse.Money money) {
        return money == null ? null : money.amount();
    }

    private String currency(CartToolResponse.Money first, CartToolResponse.Money second) {
        return currency(first, second, null);
    }

    private String currency(CartToolResponse.Money first, CartToolResponse.Money second, String fallback) {
        if (first != null && first.currency() != null && !first.currency().isBlank()) {
            return first.currency();
        }
        if (second != null && second.currency() != null && !second.currency().isBlank()) {
            return second.currency();
        }
        return fallback;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CartException(message);
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new CartException("Could not serialize cart snapshot", exception);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new CartException("SHA-256 hash algorithm is unavailable", exception);
        }
    }

    private record AppliedCodeValue(
            CartAppliedCodeType type,
            String code,
            String label,
            Boolean applicable,
            String amount,
            String currency
    ) {

        private boolean hasDisplayValue() {
            return code != null || label != null || amount != null;
        }

        private boolean sameCode(AppliedCodeValue other) {
            if (type != other.type) {
                return false;
            }
            if (code != null && other.code != null) {
                return code.equalsIgnoreCase(other.code);
            }
            return code == null
                    && other.code == null
                    && stringKey(label).equals(stringKey(other.label))
                    && stringKey(amount).equals(stringKey(other.amount));
        }

        private AppliedCodeValue merge(AppliedCodeValue other) {
            return new AppliedCodeValue(
                    type,
                    firstPresent(code, other.code),
                    firstPresent(label, other.label),
                    firstPresent(applicable, other.applicable),
                    firstPresent(amount, other.amount),
                    firstPresent(currency, other.currency)
            );
        }

        private static String stringKey(String value) {
            return value == null ? "" : value.toLowerCase();
        }

        private static <T> T firstPresent(T first, T second) {
            return first == null ? second : first;
        }
    }

}
