package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCart;
import com.meant.api.module.merchant.exception.MerchantCartException;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.command.CreateMerchantCartCommand;
import com.meant.api.module.merchant.service.command.UpdateMerchantCartCommand;
import com.meant.api.module.merchant.service.dto.CartAddItem;
import com.meant.api.module.merchant.service.dto.CartToolResult;
import com.meant.api.module.merchant.service.dto.CartUpdateItem;
import com.meant.api.module.merchant.service.dto.MerchantCartResult;
import com.meant.api.module.merchant.service.dto.MerchantCheckoutResult;
import com.meant.api.module.merchant.service.dto.UpdateCartArguments;
import com.meant.api.module.merchant.service.query.GetMerchantCartQuery;
import com.meant.api.module.merchant.service.query.GetMerchantCheckoutQuery;
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
public class MerchantCartService {

    private final MerchantRepository merchantRepository;
    private final MerchantCartPersistenceService merchantCartPersistenceService;
    private final MerchantCartClient merchantCartClient;

    public MerchantCartResult create(@NotNull @Valid CreateMerchantCartCommand command) {
        Merchant merchant = findMerchant(command.merchantId(), command.merchantDomain());
        CartToolResult result = merchantCartClient.updateCart(merchant, createArguments(command));
        return MerchantCartResult.from(merchantCartPersistenceService.saveSnapshot(null, merchant, result));
    }

    public MerchantCartResult get(@NotNull @Valid GetMerchantCartQuery query) {
        MerchantCart cart = findCart(query.cartId());
        if (!query.refresh()) {
            return MerchantCartResult.from(cart);
        }
        CartToolResult result = merchantCartClient.getCart(cart.getMerchant(), cart.getRemoteCartId());
        return MerchantCartResult.from(merchantCartPersistenceService.saveSnapshot(cart.getId(), cart.getMerchant(), result));
    }

    public MerchantCartResult update(@NotNull @Valid UpdateMerchantCartCommand command) {
        MerchantCart cart = findCart(command.cartId());
        CartToolResult result = merchantCartClient.updateCart(cart.getMerchant(), updateArguments(cart, command));
        return MerchantCartResult.from(merchantCartPersistenceService.saveSnapshot(cart.getId(), cart.getMerchant(), result));
    }

    public MerchantCheckoutResult checkout(@NotNull @Valid GetMerchantCheckoutQuery query) {
        MerchantCart cart = findCart(query.cartId());
        if (!query.refresh() && cart.getCheckoutUrl() != null && !cart.getCheckoutUrl().isBlank()) {
            return new MerchantCheckoutResult(cart.getId(), cart.getRemoteCartId(), cart.getCheckoutUrl());
        }
        CartToolResult result = merchantCartClient.getCart(cart.getMerchant(), cart.getRemoteCartId());
        MerchantCart refreshedCart = merchantCartPersistenceService.saveSnapshot(cart.getId(), cart.getMerchant(), result);
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
        return merchantCartPersistenceService.findCart(cartId);
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
        return java.util.stream.Stream.concat(localRemoteIds.stream(), safeList(command.removeRemoteCartLineIds()).stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

}
