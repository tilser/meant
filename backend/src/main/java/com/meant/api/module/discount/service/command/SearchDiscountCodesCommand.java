package com.meant.api.module.discount.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record SearchDiscountCodesCommand(
        @NotNull
        UUID userId,
        UUID merchantId,
        String merchantDomain,
        @NotEmpty
        List<@Valid Item> items,
        @Valid
        BuyerIdentity buyerIdentity,
        List<@Valid DeliveryAddressSelection> deliveryAddressesToAdd,
        List<@Valid DeliveryAddressSelection> deliveryAddressesToReplace,
        List<@Valid DeliveryOptionSelection> selectedDeliveryOptions
) {

    @AssertTrue(message = "merchantId or merchantDomain is required")
    public boolean hasMerchantIdentity() {
        return merchantId != null || (merchantDomain != null && !merchantDomain.isBlank());
    }

    public record Item(
            @NotBlank
            String productVariantId,
            @NotNull
            @Positive
            Integer quantity
    ) {
    }

    public record BuyerIdentity(
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            String countryCode
    ) {
    }

    public record DeliveryAddressSelection(
            String id,
            Boolean selected,
            @Valid
            DeliveryAddress deliveryAddress,
            String firstName,
            String lastName,
            String phoneNumber,
            String streetAddress,
            String extendedAddress,
            String city,
            String provinceCode,
            String postalCode,
            String countryCode
    ) {
    }

    public record DeliveryAddress(
            String firstName,
            String lastName,
            String phoneNumber,
            String streetAddress,
            String extendedAddress,
            String city,
            String provinceCode,
            String postalCode,
            String countryCode
    ) {
    }

    public record DeliveryOptionSelection(
            String id,
            String groupId,
            String deliveryGroupId,
            String optionHandle,
            String deliveryOptionHandle,
            String selectedOptionId
    ) {
    }
}
