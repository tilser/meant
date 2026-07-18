package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.constant.CartSnapshotPurpose;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.support.UcpCartMoney;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.module.checkout.constant.CheckoutLifecycleState;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
    public Cart saveSnapshot(
            UUID cartId, UUID userId, MerchantCartProvider provider, UcpCartToolResult result,
            CartSnapshotPurpose purpose) {
        return saveSnapshot(cartId, userId, provider, result, null, purpose);
    }

    @Transactional
    public Cart saveSnapshot(
            Cart cart, UUID userId, MerchantCartProvider provider, UcpCartToolResult result,
            CartSnapshotPurpose purpose) {
        return saveSnapshot(cart, userId, provider, result, null, purpose);
    }

    @Transactional
    public Cart saveSnapshot(
            UUID cartId,
            UUID userId,
            MerchantCartProvider provider,
            UcpCartToolResult result,
            List<String> submittedGiftCardCodes,
            CartSnapshotPurpose purpose
    ) {
        return saveSnapshot(
                cartId == null ? null : findCart(cartId, userId),
                userId,
                provider,
                result,
                submittedGiftCardCodes,
                purpose
        );
    }

    @Transactional
    public Cart saveSnapshot(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            UcpCartToolResult result,
            List<String> submittedGiftCardCodes,
            CartSnapshotPurpose purpose
    ) {
        return saveSnapshot(cart, userId, provider, result, submittedGiftCardCodes, null, List.of(), purpose);
    }

    @Transactional
    public Cart saveSnapshot(
            Cart cart,
            UUID userId,
            CartRoutingTarget target,
            UcpCartToolResult result,
            List<String> submittedGiftCardCodes,
            List<ResolvedSelectedOffer> addedOffers,
            CartSnapshotPurpose purpose
    ) {
        return saveSnapshot(
                cart, userId, target.merchantProvider(), result, submittedGiftCardCodes, target, addedOffers, purpose);
    }

    private Cart saveSnapshot(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            UcpCartToolResult result,
            List<String> submittedGiftCardCodes,
            CartRoutingTarget target,
            List<ResolvedSelectedOffer> addedOffers,
            CartSnapshotPurpose purpose
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
                : currentCartForSnapshot(cart, userId);
        if (cart != null) {
            validateWritableCart(persistedCart, userId);
        }
        boolean identityOnly = target != null && !target.scopeKey().startsWith("LEGACY:");
        if (!identityOnly) {
            persistedCart.assignProvider(provider.merchantId(), provider.domain());
        } else {
            persistedCart.assignRoutingScope(
                    target.provider().name(), target.merchantIntegrationId(), target.externalMerchantId(),
                    target.scopeKey(), provider.merchantId(), provider.domain());
        }
        String remoteCartId = required(remoteCart.id(), "Remote cart id is required");
        UcpCartResponse.Money totalAmount = remoteCart.cost() == null ? null : remoteCart.cost().totalAmount();
        UcpCartResponse.Money subtotalAmount = remoteCart.cost() == null ? null : remoteCart.cost().subtotalAmount();
        String currency = currency(totalAmount, subtotalAmount);
        persistedCart.replaceSnapshot(
                result.endpoint(),
                remoteCartId,
                identityOnly ? scopedHash(target.scopeKey(), remoteCartId) : hash(remoteCartId),
                identityOnly ? null : remoteCart.checkoutUrl(),
                identityOnly ? null : remoteCart.continueUrl(),
                identityOnly ? null : result.response().instructions(),
                identityOnly ? "{}" : result.rawResponse(),
                remoteCart.totalQuantity() == null ? totalQuantity(remoteCart.lines()) : remoteCart.totalQuantity(),
                identityOnly ? null : amount(totalAmount),
                identityOnly ? null : amount(subtotalAmount),
                identityOnly ? null : currency,
                remoteCart.createdAt(),
                remoteCart.updatedAt(),
                remoteCart.expiresAt(),
                now,
                purpose
        );
        Set<String> existingRemoteLineIds = persistedCart.getLines().stream()
                .map(CartLine::getRemoteCartLineId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        SnapshotBindings bindings = new SnapshotBindings(identityOnly ? addedOffers : List.of());
        List<CartLine> replacementLines = new ArrayList<>();
        for (UcpCartResponse.Line line : safeNonNullList(remoteCart.lines())) {
            boolean existingRemoteLine = existingRemoteLineIds.contains(line.id());
            ResolvedSelectedOffer selectedOffer = existingRemoteLine ? null : bindings.take(line);
            CartLine replacement = toCartLine(
                    line,
                    now,
                    selectedOffer,
                    identityOnly,
                    identityOnly ? target.merchantIntegrationId() : null
            );
            if (identityOnly && selectedOffer == null) {
                inheritExistingBinding(persistedCart, replacement, existingRemoteLine);
            }
            replacementLines.add(replacement);
        }
        persistedCart.replaceLines(replacementLines);
        bindings.requireConsumed();
        persistedCart.replaceAppliedCodes(identityOnly ? List.of() : toAppliedCodes(
                remoteCart, currency, submittedGiftCardCodes, persistedCart.getAppliedCodes()));
        return cartRepository.save(persistedCart);
    }

    private Cart currentCartForSnapshot(Cart expected, UUID userId) {
        Cart current = cartRepository.findForCheckoutUpdate(expected.getId(), userId)
                .orElseThrow(() -> CartException.notFound("Cart not found: " + expected.getId()));
        if (current.getCheckoutGeneration() != expected.getCheckoutGeneration()) {
            throw staleCartSnapshot();
        }
        return current;
    }

    private void inheritExistingBinding(Cart cart, CartLine replacement, boolean sameRemoteLineId) {
        List<CartLine> candidates = cart.getLines().stream()
                .filter(line -> line.getOfferKey() != null)
                .filter(line -> sameRemoteLineId
                        ? line.getRemoteCartLineId().equals(replacement.getRemoteCartLineId())
                        : line.getProductVariantId().equals(replacement.getProductVariantId()))
                .toList();
        if (candidates.size() != 1) {
            throw CartException.binding(
                    CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Remote cart line could not be matched to exactly one selected offer"
            );
        }
        replacement.inheritOfferBinding(candidates.getFirst());
    }

    @Transactional
    public Cart saveCheckoutHandoff(
            UUID cartId,
            UUID userId,
            long expectedCheckoutGeneration,
            UcpCheckoutToolResult result
    ) {
        Cart cart = cartRepository.findForCheckoutUpdate(cartId, userId)
                .orElseThrow(() -> CartException.notFound("Cart not found: " + cartId));
        validateWritableCart(cart, userId);
        if (cart.getCheckoutGeneration() != expectedCheckoutGeneration) {
            throw staleCartSnapshot();
        }
        UcpCheckoutResponse.Checkout checkout = result.response().resolvedCheckout();
        if (checkout == null) {
            throw CartException.upstream("UCP checkout response did not contain checkout");
        }
        if (hasText(checkout.cartId()) && !checkout.cartId().equals(cart.getRemoteCartId())) {
            throw CartException.upstream("UCP checkout response did not match cart");
        }
        if (!persistRawCheckout(cart) && hasText(cart.getCheckoutId()) && hasText(checkout.id())
                && !cart.getCheckoutId().equals(checkout.id())) {
            throw CartException.binding(CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Remote cart cannot change its logical checkout session");
        }
        if (!persistRawCheckout(cart) && !hasText(checkout.id())) {
            throw CartException.upstream("Provider-bound checkout response did not contain a checkout id");
        }
        String checkoutUrl = blankToNull(checkout.checkoutUrl());
        String continueUrl = blankToNull(checkout.continueUrl());
        cart.replaceCheckoutSession(
                blankToNull(checkout.id()),
                blankToNull(checkout.status()),
                checkoutUrl,
                continueUrl,
                persistRawCheckout(cart) ? result.rawResponse() : null,
                blankToNull(result.response().version()),
                CheckoutLifecycleState.from(result.response()).name(),
                Instant.now()
        );
        return cartRepository.save(cart);
    }

    private CartException staleCartSnapshot() {
        return CartException.binding(
                CartException.BindingFailure.STALE_OR_UNAVAILABLE,
                "Cart changed while the remote checkout operation was in flight"
        );
    }

    private boolean persistRawCheckout(Cart cart) {
        return cart.getRoutingScopeKey() == null || cart.getRoutingScopeKey().startsWith("LEGACY:");
    }

    @Transactional
    public void deactivate(UUID cartId, UUID userId) {
        Cart cart = findCart(cartId, userId);
        cart.deactivate(Instant.now());
        cartRepository.save(cart);
    }

    private CartLine toCartLine(UcpCartResponse.Line line, Instant now) {
        return toCartLine(line, now, null, false, null);
    }

    private CartLine toCartLine(
            UcpCartResponse.Line line,
            Instant now,
            ResolvedSelectedOffer selectedOffer,
            boolean identityOnly,
            UUID merchantIntegrationId
    ) {
        UcpCartResponse.Merchandise merchandise = line.merchandise();
        UcpCartResponse.Product product = merchandise == null ? null : merchandise.product();
        UcpCartResponse.Money totalAmount = line.cost() == null ? null : line.cost().totalAmount();
        UcpCartResponse.Money subtotalAmount = line.cost() == null ? null : line.cost().subtotalAmount();
        CartLine.CartLineBuilder builder = CartLine.builder()
                .remoteCartLineId(required(line.id(), "Remote cart line id is required"))
                .productId(identityOnly ? null : product == null ? null : product.id())
                .productTitle(identityOnly ? null : product == null ? null : product.title())
                .productVariantId(required(merchandise == null ? null : merchandise.id(), "Product variant id is required"))
                .variantTitle(identityOnly ? null : merchandise == null ? null : merchandise.title())
                .quantity(line.quantity() == null ? 0 : line.quantity())
                .totalAmount(identityOnly ? null : amount(totalAmount))
                .subtotalAmount(identityOnly ? null : amount(subtotalAmount))
                .currency(identityOnly ? null : currency(totalAmount, subtotalAmount))
                .rawLineResponse(identityOnly ? "{}" : toJson(line))
                .createdAt(now)
                .updatedAt(now);
        if (selectedOffer != null) {
            var identity = selectedOffer.identity();
            var merchant = identity.merchantScope().externalMerchantIdentity();
            var source = selectedOffer.provenance().discoverySource();
            var facts = selectedOffer.commercialFacts();
            builder.provider(identity.provider().value())
                    .merchantIntegrationId(merchantIntegrationId)
                    .externalMerchantId(merchant == null ? null : merchant.value())
                    .externalProductId(selectedOffer.provenance().externalProductReference().value())
                    .externalVariantId(selectedOffer.provenance().externalVariantReference() == null
                            ? null : selectedOffer.provenance().externalVariantReference().value())
                    .offerProductId(identity.externalProductIdentity().value())
                    .offerVariantId(identity.externalVariantIdentity() == null
                            ? null : identity.externalVariantIdentity().value())
                    .offerKey(selectedOffer.offerKey())
                    .canonicalProductKey(selectedOffer.canonicalProductKey())
                    .sourceType(source.type().name())
                    .sourceIdentity(source.value())
                    .selectedOptionsJson(toJson(identity.selectedOptions()))
                    .componentsJson(toJson(identity.components()))
                    .sellingPlanJson(identity.sellingPlanIdentity() == null
                            ? null : toJson(identity.sellingPlanIdentity()))
                    .productTitle(facts == null ? null : blankToNull(facts.title()))
                    .productBrand(facts == null ? null : blankToNull(facts.merchantName()))
                    .imageUrl(firstMediaUrl(facts))
                    .productUrl(sourceProductUrl(facts))
                    .selectedAt(now);
        }
        return builder.build();
    }

    private String firstMediaUrl(RehydratedCommercialFacts facts) {
        if (facts == null) {
            return null;
        }
        return facts.sourceMedia().stream()
                .filter(Objects::nonNull)
                .map(ProductMedia::url)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }

    private String sourceProductUrl(RehydratedCommercialFacts facts) {
        return facts == null || facts.productUrl() == null ? null : facts.productUrl().toString();
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
        if (cart == null) {
            throw CartException.notFound("Cart not found");
        }
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

    private String scopedHash(String scope, String remoteCartId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateLengthPrefixed(digest, scope);
            updateLengthPrefixed(digest, remoteCartId);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new CartException("SHA-256 hash algorithm is unavailable", exception);
        }
    }

    private void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
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

    private static final class SnapshotBindings {
        private final java.util.Map<String, ResolvedSelectedOffer> remaining;

        private SnapshotBindings(List<ResolvedSelectedOffer> offers) {
            this.remaining = new java.util.LinkedHashMap<>();
            for (ResolvedSelectedOffer offer : offers == null ? List.<ResolvedSelectedOffer>of() : offers) {
                String variant = offer.identity().externalVariantIdentity() == null
                        ? null : offer.identity().externalVariantIdentity().value();
                if (variant == null || remaining.putIfAbsent(variant, offer) != null) {
                    throw CartException.binding(
                            CartException.BindingFailure.IDENTITY_MISMATCH,
                            "Selected offers cannot be disambiguated by the remote cart response");
                }
            }
        }

        private ResolvedSelectedOffer take(UcpCartResponse.Line line) {
            String variantId = line.merchandise() == null ? null : line.merchandise().id();
            return variantId == null ? null : remaining.remove(variantId);
        }

        private void requireConsumed() {
            if (!remaining.isEmpty()) {
                throw CartException.upstream("Remote cart response did not preserve selected offer identity");
            }
        }
    }

}
