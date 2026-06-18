package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
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
    public Cart findCart(UUID cartId) {
        return cartRepository.findWithLinesById(cartId)
                .orElseThrow(() -> CartException.notFound("Cart not found: " + cartId));
    }

    @Transactional
    public Cart saveSnapshot(UUID cartId, MerchantCartProvider provider, CartToolResult result) {
        CartToolResponse.Cart remoteCart = result.response().cart();
        Instant now = Instant.now();
        Cart cart = cartId == null
                ? Cart.builder()
                        .merchantId(provider.merchantId())
                        .merchantDomain(provider.domain())
                        .createdAt(now)
                        .build()
                : findCart(cartId);
        cart.assignProvider(provider.merchantId(), provider.domain());
        String remoteCartId = required(remoteCart.id(), "Remote cart id is required");
        CartToolResponse.Money totalAmount = remoteCart.cost() == null ? null : remoteCart.cost().totalAmount();
        CartToolResponse.Money subtotalAmount = remoteCart.cost() == null ? null : remoteCart.cost().subtotalAmount();
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
                currency(totalAmount, subtotalAmount),
                remoteCart.createdAt(),
                remoteCart.updatedAt(),
                now
        );
        cart.replaceLines(safeNonNullList(remoteCart.lines()).stream()
                .map(line -> toCartLine(line, now))
                .toList());
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
        if (first != null && first.currency() != null && !first.currency().isBlank()) {
            return first.currency();
        }
        return second == null ? null : second.currency();
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

}
