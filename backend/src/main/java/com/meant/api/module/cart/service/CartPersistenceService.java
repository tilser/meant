package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.support.UcpCartMoney;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
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
        Hibernate.initialize(cart.getAppliedCodes());
        if (cart.getExpiresAt() != null && !cart.getExpiresAt().isAfter(Instant.now())) {
            throw CartException.notFound("Cart expired: " + cartId);
        }
        return cart;
    }

    @Transactional
    public Cart saveSnapshot(UUID cartId, UUID userId, MerchantCartProvider provider, UcpCartToolResult result) {
        return saveSnapshot(cartId, userId, provider, result, null);
    }

    @Transactional
    public Cart saveSnapshot(Cart cart, UUID userId, MerchantCartProvider provider, UcpCartToolResult result) {
        return saveSnapshot(cart, userId, provider, result, null);
    }

    @Transactional
    public Cart saveSnapshot(
            UUID cartId,
            UUID userId,
            MerchantCartProvider provider,
            UcpCartToolResult result,
            List<String> submittedGiftCardCodes
    ) {
        return saveSnapshot(
                cartId == null ? null : findCart(cartId, userId),
                userId,
                provider,
                result,
                submittedGiftCardCodes
        );
    }

    @Transactional
    public Cart saveSnapshot(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            UcpCartToolResult result,
            List<String> submittedGiftCardCodes
    ) {
        UcpCartResponse.Cart remoteCart = result.response().cart();
        Instant now = Instant.now();
        Cart persistedCart = cart == null
                ? Cart.builder()
                        .userId(userId)
                        .merchantId(provider.merchantId())
                        .merchantDomain(provider.domain())
                        .createdAt(now)
                        .build()
                : cart;
        if (cart != null) {
            validateWritableCart(persistedCart, userId);
        }
        persistedCart.assignProvider(provider.merchantId(), provider.domain());
        String remoteCartId = required(remoteCart.id(), "Remote cart id is required");
        UcpCartResponse.Money totalAmount = remoteCart.cost() == null ? null : remoteCart.cost().totalAmount();
        UcpCartResponse.Money subtotalAmount = remoteCart.cost() == null ? null : remoteCart.cost().subtotalAmount();
        String currency = currency(totalAmount, subtotalAmount);
        persistedCart.replaceSnapshot(
                result.endpoint(),
                remoteCartId,
                hash(remoteCartId),
                remoteCart.checkoutUrl(),
                remoteCart.continueUrl(),
                result.response().instructions(),
                result.rawResponse(),
                remoteCart.totalQuantity() == null ? totalQuantity(remoteCart.lines()) : remoteCart.totalQuantity(),
                amount(totalAmount),
                amount(subtotalAmount),
                currency,
                remoteCart.createdAt(),
                remoteCart.updatedAt(),
                remoteCart.expiresAt(),
                now
        );
        persistedCart.replaceLines(safeNonNullList(remoteCart.lines()).stream()
                .map(line -> toCartLine(line, now))
                .toList());
        persistedCart.replaceAppliedCodes(toAppliedCodes(
                remoteCart,
                currency,
                submittedGiftCardCodes,
                persistedCart.getAppliedCodes()
        ));
        return cartRepository.save(persistedCart);
    }

    @Transactional
    public Cart saveCheckoutHandoff(UUID cartId, UUID userId, UcpCheckoutToolResult result) {
        return saveCheckoutHandoff(findCart(cartId, userId), userId, result);
    }

    @Transactional
    public Cart saveCheckoutHandoff(Cart cart, UUID userId, UcpCheckoutToolResult result) {
        validateWritableCart(cart, userId);
        UcpCheckoutResponse.Checkout checkout = result.response().resolvedCheckout();
        if (checkout == null) {
            throw CartException.upstream("UCP checkout response did not contain checkout");
        }
        if (hasText(checkout.cartId()) && !checkout.cartId().equals(cart.getRemoteCartId())) {
            throw CartException.upstream("UCP checkout response did not match cart");
        }
        String checkoutUrl = blankToNull(checkout.checkoutUrl());
        String continueUrl = blankToNull(checkout.continueUrl());
        if (checkoutUrl == null && continueUrl == null) {
            throw CartException.upstream("UCP checkout response did not contain a handoff URL");
        }
        cart.replaceCheckoutHandoff(checkoutUrl, continueUrl, Instant.now());
        return cartRepository.save(cart);
    }

    @Transactional
    public void deactivate(UUID cartId, UUID userId) {
        Cart cart = findCart(cartId, userId);
        cart.deactivate(Instant.now());
        cartRepository.save(cart);
    }

    private CartLine toCartLine(UcpCartResponse.Line line, Instant now) {
        UcpCartResponse.Merchandise merchandise = line.merchandise();
        UcpCartResponse.Product product = merchandise == null ? null : merchandise.product();
        UcpCartResponse.Money totalAmount = line.cost() == null ? null : line.cost().totalAmount();
        UcpCartResponse.Money subtotalAmount = line.cost() == null ? null : line.cost().subtotalAmount();
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

    private List<CartAppliedCode> toAppliedCodes(
            UcpCartResponse.Cart remoteCart,
            String cartCurrency,
            List<String> submittedGiftCardCodes,
            List<CartAppliedCode> existingAppliedCodes
    ) {
        List<AppliedCodeValue> values = new ArrayList<>();
        List<String> knownGiftCardCodes = knownGiftCardCodes(submittedGiftCardCodes, existingAppliedCodes);
        addAppliedCodeValues(values, CartAppliedCodeType.DISCOUNT, remoteCart.discountCodes(), cartCurrency, knownGiftCardCodes);
        addAppliedCodeValues(values, CartAppliedCodeType.DISCOUNT, remoteCart.appliedDiscounts(), cartCurrency, knownGiftCardCodes);
        addAppliedCodeValues(values, CartAppliedCodeType.DISCOUNT, remoteCart.discountAllocations(), cartCurrency, knownGiftCardCodes);
        addAppliedCodeValues(values, CartAppliedCodeType.GIFT_CARD, remoteCart.giftCardCodes(), cartCurrency, knownGiftCardCodes);
        addAppliedCodeValues(values, CartAppliedCodeType.GIFT_CARD, remoteCart.appliedGiftCards(), cartCurrency, knownGiftCardCodes);

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

    private void validateWritableCart(Cart cart, UUID userId) {
        if (!Objects.equals(cart.getUserId(), userId) || !cart.isActive()) {
            throw CartException.notFound("Cart not found: " + cart.getId());
        }
        if (cart.getExpiresAt() != null && !cart.getExpiresAt().isAfter(Instant.now())) {
            throw CartException.notFound("Cart expired: " + cart.getId());
        }
    }

    private void addAppliedCodeValues(
            List<AppliedCodeValue> values,
            CartAppliedCodeType type,
            List<UcpCartResponse.AppliedCode> appliedCodes,
            String cartCurrency,
            List<String> knownGiftCardCodes
    ) {
        for (UcpCartResponse.AppliedCode appliedCode : safeNonNullList(appliedCodes)) {
            String remoteCode = blankToNull(appliedCode.code());
            AppliedCodeValue value = new AppliedCodeValue(
                    type,
                    resolvedCode(type, remoteCode, knownGiftCardCodes),
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

    private List<String> knownGiftCardCodes(List<String> submittedGiftCardCodes, List<CartAppliedCode> existingAppliedCodes) {
        return java.util.stream.Stream.concat(
                        safeNonNullList(submittedGiftCardCodes).stream(),
                        safeNonNullList(existingAppliedCodes).stream()
                                .filter(code -> code.getType() == CartAppliedCodeType.GIFT_CARD)
                                .map(CartAppliedCode::getCode)
                )
                .map(this::blankToNull)
                .filter(code -> code != null)
                .distinct()
                .toList();
    }

    private String resolvedCode(CartAppliedCodeType type, String remoteCode, List<String> knownGiftCardCodes) {
        if (type != CartAppliedCodeType.GIFT_CARD || remoteCode == null) {
            return remoteCode;
        }

        String normalizedRemoteCode = remoteCode.toLowerCase(Locale.ROOT);
        List<String> matchingKnownCodes = knownGiftCardCodes.stream()
                .filter(knownCode -> {
                    String normalizedKnownCode = knownCode.toLowerCase(Locale.ROOT);
                    return !normalizedKnownCode.equals(normalizedRemoteCode)
                            && normalizedKnownCode.endsWith(normalizedRemoteCode);
                })
                .distinct()
                .toList();
        return matchingKnownCodes.size() == 1 ? matchingKnownCodes.getFirst() : remoteCode;
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

    private int totalQuantity(List<UcpCartResponse.Line> lines) {
        return safeNonNullList(lines).stream()
                .map(UcpCartResponse.Line::quantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String amount(UcpCartResponse.Money money) {
        return UcpCartMoney.displayAmount(money);
    }

    private String currency(UcpCartResponse.Money first, UcpCartResponse.Money second) {
        return UcpCartMoney.currency(first, second);
    }

    private String currency(UcpCartResponse.Money first, UcpCartResponse.Money second, String fallback) {
        return UcpCartMoney.currency(first, second, fallback);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }

        private static <T> T firstPresent(T first, T second) {
            return first == null ? second : first;
        }
    }

}
