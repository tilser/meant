package com.meant.api.module.cart.controller.mapper;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.cart.controller.request.CartCreateRequest;
import com.meant.api.module.cart.controller.request.CartUpdateRequest;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CartCommandMapper {

    public static CreateCartCommand toCommand(CartCreateRequest request) {
        return new CreateCartCommand(
                request.merchantId(),
                request.merchantDomain(),
                safeList(request.addItems()).stream()
                        .map(item -> new CreateCartCommand.AddItem(
                                item.productVariantId(),
                                item.quantity()
                        ))
                        .toList(),
                request.buyerIdentity(),
                safeList(request.deliveryAddressesToAdd()),
                safeList(request.deliveryAddressesToReplace()),
                safeList(request.selectedDeliveryOptions()),
                safeList(request.discountCodes()),
                safeList(request.giftCardCodes()),
                request.note()
        );
    }

    public static UpdateCartCommand toCommand(UUID cartId, CartUpdateRequest request) {
        return new UpdateCartCommand(
                cartId,
                safeList(request.addItems()).stream()
                        .map(item -> new UpdateCartCommand.AddItem(
                                item.productVariantId(),
                                item.quantity()
                        ))
                        .toList(),
                safeList(request.updateItems()).stream()
                        .map(item -> new UpdateCartCommand.UpdateItem(
                                item.cartLineId(),
                                item.remoteCartLineId(),
                                item.quantity()
                        ))
                        .toList(),
                safeList(request.removeCartLineIds()),
                safeList(request.removeRemoteCartLineIds()),
                request.buyerIdentity(),
                safeList(request.deliveryAddressesToAdd()),
                safeList(request.deliveryAddressesToReplace()),
                safeList(request.selectedDeliveryOptions()),
                safeList(request.discountCodes()),
                safeList(request.giftCardCodes()),
                request.note()
        );
    }

}
