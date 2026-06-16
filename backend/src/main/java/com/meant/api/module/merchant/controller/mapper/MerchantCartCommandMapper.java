package com.meant.api.module.merchant.controller.mapper;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.controller.request.MerchantCartCreateRequest;
import com.meant.api.module.merchant.controller.request.MerchantCartUpdateRequest;
import com.meant.api.module.merchant.service.command.CreateMerchantCartCommand;
import com.meant.api.module.merchant.service.command.UpdateMerchantCartCommand;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MerchantCartCommandMapper {

    public static CreateMerchantCartCommand toCommand(MerchantCartCreateRequest request) {
        return new CreateMerchantCartCommand(
                request.merchantId(),
                request.merchantDomain(),
                safeList(request.addItems()).stream()
                        .map(item -> new CreateMerchantCartCommand.AddItem(
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

    public static UpdateMerchantCartCommand toCommand(UUID cartId, MerchantCartUpdateRequest request) {
        return new UpdateMerchantCartCommand(
                cartId,
                safeList(request.addItems()).stream()
                        .map(item -> new UpdateMerchantCartCommand.AddItem(
                                item.productVariantId(),
                                item.quantity()
                        ))
                        .toList(),
                safeList(request.updateItems()).stream()
                        .map(item -> new UpdateMerchantCartCommand.UpdateItem(
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
