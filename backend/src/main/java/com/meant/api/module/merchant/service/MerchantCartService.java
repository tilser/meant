package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCart;
import com.meant.api.module.merchant.entity.MerchantCartLine;
import com.meant.api.module.merchant.exception.MerchantCartException;
import com.meant.api.module.merchant.repository.MerchantCartRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.command.CreateMerchantCartCommand;
import com.meant.api.module.merchant.service.command.UpdateMerchantCartCommand;
import com.meant.api.module.merchant.service.dto.CartAddItem;
import com.meant.api.module.merchant.service.dto.CartToolResponse;
import com.meant.api.module.merchant.service.dto.CartToolResult;
import com.meant.api.module.merchant.service.dto.CartUpdateItem;
import com.meant.api.module.merchant.service.dto.MerchantCartResult;
import com.meant.api.module.merchant.service.dto.MerchantCheckoutResult;
import com.meant.api.module.merchant.service.dto.UpdateCartArguments;
import com.meant.api.module.merchant.service.query.GetMerchantCartQuery;
import com.meant.api.module.merchant.service.query.GetMerchantCheckoutQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantCartService {

    private final MerchantRepository merchantRepository;
    private final MerchantCartRepository merchantCartRepository;
    private final MerchantCartClient merchantCartClient;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    public MerchantCartResult create(@NotNull @Valid CreateMerchantCartCommand command) {
        Merchant merchant = findMerchant(command.merchantId(), command.merchantDomain());
        CartToolResult result = merchantCartClient.updateCart(merchant, createArguments(command));
        return transactionTemplate().execute(status -> MerchantCartResult.from(saveSnapshot(null, merchant, result)));
    }

    public MerchantCartResult get(@NotNull @Valid GetMerchantCartQuery query) {
        MerchantCart cart = findCart(query.cartId());
        if (!query.refresh()) {
            return MerchantCartResult.from(cart);
        }
        CartToolResult result = merchantCartClient.getCart(cart.getMerchant(), cart.getRemoteCartId());
        return transactionTemplate().execute(
                status -> MerchantCartResult.from(saveSnapshot(cart.getId(), cart.getMerchant(), result))
        );
    }

    public MerchantCartResult update(@NotNull @Valid UpdateMerchantCartCommand command) {
        MerchantCart cart = findCart(command.cartId());
        CartToolResult result = merchantCartClient.updateCart(cart.getMerchant(), updateArguments(cart, command));
        return transactionTemplate().execute(
                status -> MerchantCartResult.from(saveSnapshot(cart.getId(), cart.getMerchant(), result))
        );
    }

    public MerchantCheckoutResult checkout(@NotNull @Valid GetMerchantCheckoutQuery query) {
        MerchantCart cart = findCart(query.cartId());
        if (!query.refresh() && cart.getCheckoutUrl() != null && !cart.getCheckoutUrl().isBlank()) {
            return new MerchantCheckoutResult(cart.getId(), cart.getRemoteCartId(), cart.getCheckoutUrl());
        }
        CartToolResult result = merchantCartClient.getCart(cart.getMerchant(), cart.getRemoteCartId());
        MerchantCart refreshedCart = transactionTemplate().execute(
                status -> saveSnapshot(cart.getId(), cart.getMerchant(), result)
        );
        return new MerchantCheckoutResult(
                refreshedCart.getId(),
                refreshedCart.getRemoteCartId(),
                refreshedCart.getCheckoutUrl()
        );
    }

    private Merchant findMerchant(UUID merchantId, String merchantDomain) {
        if (merchantId != null) {
            return merchantRepository.findById(merchantId)
                    .orElseThrow(() -> new MerchantCartException("Merchant not found: " + merchantId));
        }
        if (merchantDomain != null && !merchantDomain.isBlank()) {
            return merchantRepository.findByDomain(merchantDomain.trim())
                    .orElseThrow(() -> new MerchantCartException("Merchant not found: " + merchantDomain));
        }
        throw new MerchantCartException("merchantId or merchantDomain is required");
    }

    private MerchantCart findCart(UUID cartId) {
        return merchantCartRepository.findWithMerchantAndLinesById(cartId)
                .orElseThrow(() -> new MerchantCartException("Merchant cart not found: " + cartId));
    }

    private UpdateCartArguments createArguments(CreateMerchantCartCommand command) {
        return new UpdateCartArguments(
                null,
                safeList(command.addItems()).stream()
                        .map(item -> new CartAddItem(item.productVariantId(), item.quantity()))
                        .toList(),
                List.of(),
                List.of(),
                command.buyerIdentity(),
                safeList(command.deliveryAddressesToAdd()),
                safeList(command.deliveryAddressesToReplace()),
                safeList(command.selectedDeliveryOptions()),
                safeList(command.discountCodes()),
                safeList(command.giftCardCodes()),
                command.note()
        );
    }

    private UpdateCartArguments updateArguments(MerchantCart cart, UpdateMerchantCartCommand command) {
        Map<UUID, String> remoteLineIdsByLocalId = new HashMap<>();
        cart.getLines().forEach(line -> remoteLineIdsByLocalId.put(line.getId(), line.getRemoteCartLineId()));
        return new UpdateCartArguments(
                cart.getRemoteCartId(),
                safeList(command.addItems()).stream()
                        .map(item -> new CartAddItem(item.productVariantId(), item.quantity()))
                        .toList(),
                safeList(command.updateItems()).stream()
                        .map(item -> new CartUpdateItem(remoteCartLineId(item, remoteLineIdsByLocalId), item.quantity()))
                        .toList(),
                removeLineIds(command, remoteLineIdsByLocalId),
                command.buyerIdentity(),
                safeList(command.deliveryAddressesToAdd()),
                safeList(command.deliveryAddressesToReplace()),
                safeList(command.selectedDeliveryOptions()),
                safeList(command.discountCodes()),
                safeList(command.giftCardCodes()),
                command.note()
        );
    }

    private String remoteCartLineId(
            UpdateMerchantCartCommand.UpdateItem item,
            Map<UUID, String> remoteLineIdsByLocalId
    ) {
        if (item.remoteCartLineId() != null && !item.remoteCartLineId().isBlank()) {
            return item.remoteCartLineId();
        }
        if (item.cartLineId() == null) {
            throw new MerchantCartException("cartLineId or remoteCartLineId is required for update item");
        }
        String remoteCartLineId = remoteLineIdsByLocalId.get(item.cartLineId());
        if (remoteCartLineId == null) {
            throw new MerchantCartException("Cart line not found: " + item.cartLineId());
        }
        return remoteCartLineId;
    }

    private List<String> removeLineIds(
            UpdateMerchantCartCommand command,
            Map<UUID, String> remoteLineIdsByLocalId
    ) {
        List<String> localRemoteIds = safeList(command.removeCartLineIds()).stream()
                .map(cartLineId -> {
                    String remoteCartLineId = remoteLineIdsByLocalId.get(cartLineId);
                    if (remoteCartLineId == null) {
                        throw new MerchantCartException("Cart line not found: " + cartLineId);
                    }
                    return remoteCartLineId;
                })
                .toList();
        return List.copyOf(new java.util.LinkedHashSet<>(
                java.util.stream.Stream.concat(localRemoteIds.stream(), safeList(command.removeRemoteCartLineIds()).stream())
                        .filter(value -> value != null && !value.isBlank())
                        .toList()
        ));
    }

    private MerchantCart saveSnapshot(UUID cartId, Merchant merchant, CartToolResult result) {
        CartToolResponse.Cart remoteCart = result.response().cart();
        Instant now = Instant.now();
        MerchantCart cart = cartId == null
                ? MerchantCart.builder()
                        .merchant(merchant)
                        .createdAt(now)
                        .build()
                : findCart(cartId);
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
        cart.replaceLines(safeList(remoteCart.lines()).stream()
                .map(line -> toCartLine(line, now))
                .toList());
        return merchantCartRepository.save(cart);
    }

    private MerchantCartLine toCartLine(CartToolResponse.Line line, Instant now) {
        CartToolResponse.Merchandise merchandise = line.merchandise();
        CartToolResponse.Product product = merchandise == null ? null : merchandise.product();
        CartToolResponse.Money totalAmount = line.cost() == null ? null : line.cost().totalAmount();
        CartToolResponse.Money subtotalAmount = line.cost() == null ? null : line.cost().subtotalAmount();
        return MerchantCartLine.builder()
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
        return safeList(lines).stream()
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
            throw new MerchantCartException(message);
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new MerchantCartException("Could not serialize cart snapshot", exception);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new MerchantCartException("SHA-256 hash algorithm is unavailable", exception);
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
