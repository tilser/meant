package com.meant.api.module.merchant.controller;

import com.meant.api.module.merchant.controller.request.MerchantCartCreateRequest;
import com.meant.api.module.merchant.controller.request.MerchantCartUpdateRequest;
import com.meant.api.module.merchant.controller.response.MerchantCartResponse;
import com.meant.api.module.merchant.controller.response.MerchantCheckoutResponse;
import com.meant.api.module.merchant.service.MerchantCartService;
import com.meant.api.module.merchant.service.command.CreateMerchantCartCommand;
import com.meant.api.module.merchant.service.command.UpdateMerchantCartCommand;
import com.meant.api.module.merchant.service.query.GetMerchantCartQuery;
import com.meant.api.module.merchant.service.query.GetMerchantCheckoutQuery;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant-carts")
@RequiredArgsConstructor
public class MerchantCartController {

    private final MerchantCartService merchantCartService;

    @PostMapping
    public MerchantCartResponse create(@Valid @RequestBody MerchantCartCreateRequest request) {
        return MerchantCartResponse.from(merchantCartService.create(toCommand(request)));
    }

    @GetMapping("/{cartId}")
    public MerchantCartResponse get(
            @PathVariable UUID cartId,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return MerchantCartResponse.from(merchantCartService.get(new GetMerchantCartQuery(cartId, refresh)));
    }

    @PatchMapping("/{cartId}")
    public MerchantCartResponse update(
            @PathVariable UUID cartId,
            @Valid @RequestBody MerchantCartUpdateRequest request
    ) {
        return MerchantCartResponse.from(merchantCartService.update(toCommand(cartId, request)));
    }

    @GetMapping("/{cartId}/checkout")
    public MerchantCheckoutResponse checkout(
            @PathVariable UUID cartId,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return MerchantCheckoutResponse.from(
                merchantCartService.checkout(new GetMerchantCheckoutQuery(cartId, refresh))
        );
    }

    private CreateMerchantCartCommand toCommand(MerchantCartCreateRequest request) {
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

    private UpdateMerchantCartCommand toCommand(UUID cartId, MerchantCartUpdateRequest request) {
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

}
