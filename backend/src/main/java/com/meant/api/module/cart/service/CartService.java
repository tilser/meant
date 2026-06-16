package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.dto.CartAddItem;
import com.meant.api.module.cart.service.dto.CartToolResult;
import com.meant.api.module.cart.service.dto.CartUpdateItem;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.dto.UpdateCartArguments;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class CartService {

    private final MerchantCartProviderLookupService merchantCartProviderLookupService;
    private final CartPersistenceService cartPersistenceService;
    private final CartClient cartClient;

    public CartResult create(@NotNull @Valid CreateCartCommand command) {
        MerchantCartProvider provider = findProvider(command.merchantId(), command.merchantDomain());
        CartToolResult result = cartClient.updateCart(provider, createArguments(command));
        return CartResult.from(cartPersistenceService.saveSnapshot(null, provider, result));
    }

    public CartResult get(@NotNull @Valid GetCartQuery query) {
        Cart cart = findCart(query.cartId());
        if (!query.refresh()) {
            return CartResult.from(cart);
        }
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        CartToolResult result = cartClient.getCart(provider, cart.getRemoteCartId());
        return CartResult.from(cartPersistenceService.saveSnapshot(cart.getId(), provider, result));
    }

    public CartResult update(@NotNull @Valid UpdateCartCommand command) {
        Cart cart = findCart(command.cartId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        CartToolResult result = cartClient.updateCart(provider, updateArguments(cart, command));
        return CartResult.from(cartPersistenceService.saveSnapshot(cart.getId(), provider, result));
    }

    public CheckoutResult checkout(@NotNull @Valid GetCheckoutQuery query) {
        Cart cart = findCart(query.cartId());
        if (!query.refresh() && cart.getCheckoutUrl() != null && !cart.getCheckoutUrl().isBlank()) {
            return new CheckoutResult(cart.getId(), cart.getRemoteCartId(), cart.getCheckoutUrl());
        }
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        CartToolResult result = cartClient.getCart(provider, cart.getRemoteCartId());
        Cart refreshedCart = cartPersistenceService.saveSnapshot(cart.getId(), provider, result);
        return new CheckoutResult(
                refreshedCart.getId(),
                refreshedCart.getRemoteCartId(),
                refreshedCart.getCheckoutUrl()
        );
    }

    private MerchantCartProvider findProvider(UUID merchantId, String merchantDomain) {
        if (merchantId != null) {
            return merchantCartProviderLookupService.findById(merchantId)
                    .orElseThrow(() -> new CartException("Merchant not found: " + merchantId));
        }
        if (merchantDomain != null && !merchantDomain.isBlank()) {
            return merchantCartProviderLookupService.findByDomain(merchantDomain)
                    .orElseThrow(() -> new CartException("Merchant not found: " + merchantDomain));
        }
        throw new CartException("merchantId or merchantDomain is required");
    }

    private Cart findCart(UUID cartId) {
        return cartPersistenceService.findCart(cartId);
    }

    private UpdateCartArguments createArguments(CreateCartCommand command) {
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

    private UpdateCartArguments updateArguments(Cart cart, UpdateCartCommand command) {
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
            UpdateCartCommand.UpdateItem item,
            Map<UUID, String> remoteLineIdsByLocalId
    ) {
        if (item.remoteCartLineId() != null && !item.remoteCartLineId().isBlank()) {
            return item.remoteCartLineId();
        }
        if (item.cartLineId() == null) {
            throw new CartException("cartLineId or remoteCartLineId is required for update item");
        }
        String remoteCartLineId = remoteLineIdsByLocalId.get(item.cartLineId());
        if (remoteCartLineId == null) {
            throw new CartException("Cart line not found: " + item.cartLineId());
        }
        return remoteCartLineId;
    }

    private List<String> removeLineIds(
            UpdateCartCommand command,
            Map<UUID, String> remoteLineIdsByLocalId
    ) {
        List<String> localRemoteIds = safeList(command.removeCartLineIds()).stream()
                .map(cartLineId -> {
                    String remoteCartLineId = remoteLineIdsByLocalId.get(cartLineId);
                    if (remoteCartLineId == null) {
                        throw new CartException("Cart line not found: " + cartLineId);
                    }
                    return remoteCartLineId;
                })
                .toList();
        return java.util.stream.Stream.concat(localRemoteIds.stream(), safeList(command.removeRemoteCartLineIds()).stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

}
