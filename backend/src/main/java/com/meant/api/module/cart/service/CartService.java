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
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
    private final UserInventoryService userInventoryService;

    public CartResult create(@NotNull @Valid CreateCartCommand command) {
        MerchantCartProvider provider = findProvider(command.merchantId(), command.merchantDomain());
        UpdateCartArguments arguments = createArguments(command);
        CartToolResult result = cartClient.updateCart(provider, arguments);
        return CartResult.from(cartPersistenceService.saveSnapshot(null, command.userId(), provider, result, arguments));
    }

    public CartResult get(@NotNull @Valid GetCartQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        if (!query.refresh()) {
            return CartResult.from(cart);
        }
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        CartToolResult result = cartClient.getCart(provider, cart.getRemoteCartId());
        return CartResult.from(cartPersistenceService.saveSnapshot(cart.getId(), query.userId(), provider, result));
    }

    public CartResult update(@NotNull @Valid UpdateCartCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        UpdateCartArguments arguments = updateArguments(cart, command);
        CartToolResult result = cartClient.updateCart(provider, arguments);
        return CartResult.from(cartPersistenceService.saveSnapshot(cart.getId(), command.userId(), provider, result, arguments));
    }

    public CheckoutResult checkout(@NotNull @Valid GetCheckoutQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        if (!query.refresh() && cart.getCheckoutUrl() != null && !cart.getCheckoutUrl().isBlank()) {
            importCartInventory(cart);
            return new CheckoutResult(cart.getId(), cart.getRemoteCartId(), cart.getCheckoutUrl());
        }
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        CartToolResult result = cartClient.getCart(provider, cart.getRemoteCartId());
        Cart refreshedCart = cartPersistenceService.saveSnapshot(cart.getId(), query.userId(), provider, result);
        importCartInventory(refreshedCart);
        return new CheckoutResult(
                refreshedCart.getId(),
                refreshedCart.getRemoteCartId(),
                refreshedCart.getCheckoutUrl()
        );
    }

    private MerchantCartProvider findProvider(UUID merchantId, String merchantDomain) {
        if (merchantId != null) {
            return merchantCartProviderLookupService.findById(merchantId)
                    .orElseThrow(() -> CartException.notFound("Merchant not found: " + merchantId));
        }
        if (merchantDomain != null && !merchantDomain.isBlank()) {
            return merchantCartProviderLookupService.findByDomain(merchantDomain)
                    .orElseThrow(() -> CartException.notFound("Merchant not found: " + merchantDomain));
        }
        throw new CartException("merchantId or merchantDomain is required");
    }

    private Cart findCart(UUID cartId, UUID userId) {
        return cartPersistenceService.findCart(cartId, userId);
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
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
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
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
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
            throw CartException.notFound("Cart line not found: " + item.cartLineId());
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
                        throw CartException.notFound("Cart line not found: " + cartLineId);
                    }
                    return remoteCartLineId;
                })
                .toList();
        return java.util.stream.Stream.concat(localRemoteIds.stream(), safeList(command.removeRemoteCartLineIds()).stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return null;
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void importCartInventory(Cart cart) {
        Instant purchasedAt = inventoryPurchasedAt(cart);
        List<ImportPurchasedInventoryItemsCommand.PurchasedItem> items = cart.getLines().stream()
                .filter(line -> line.getProductVariantId() != null && !line.getProductVariantId().isBlank())
                .map(line -> new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        cart.getMerchantDomain() + ":" + line.getProductVariantId(),
                        null,
                        productName(line.getProductTitle(), line.getVariantTitle(), line.getProductVariantId()),
                        cart.getMerchantDomain(),
                        null,
                        null,
                        line.getQuantity(),
                        purchasedAt
                ))
                .toList();
        if (!items.isEmpty()) {
            userInventoryService.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(cart.getUserId(), items));
        }
    }

    private Instant inventoryPurchasedAt(Cart cart) {
        if (cart.getRemoteUpdatedAt() != null) {
            return cart.getRemoteUpdatedAt();
        }
        if (cart.getUpdatedAt() != null) {
            return cart.getUpdatedAt();
        }
        return cart.getRefreshedAt();
    }

    private String productName(String productTitle, String variantTitle, String fallback) {
        if (productTitle != null && !productTitle.isBlank()) {
            return productTitle;
        }
        if (variantTitle != null && !variantTitle.isBlank()) {
            return variantTitle;
        }
        return fallback;
    }

}
