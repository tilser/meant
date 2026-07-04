package com.meant.api.module.discount.controller.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

@Schema(name = "SearchDiscountCodesRequest", description = "Cart context used to discover and validate merchant discount codes.")
public record SearchDiscountCodesRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Local merchant UUID. Required when merchantDomain is omitted.", example = "00000000-0000-0000-0000-000000000001")
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Merchant storefront domain. Required when merchantId is omitted.", example = "merchant.example")
        String merchantDomain,
        @NotEmpty
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Cart items used to validate each candidate code.")
        List<@Valid Item> items,
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer identity forwarded to the merchant cart provider.")
        BuyerIdentity buyerIdentity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Delivery addresses to add before validating discount codes.")
        List<@Valid DeliveryAddressSelection> deliveryAddressesToAdd,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Delivery addresses to replace before validating discount codes.")
        List<@Valid DeliveryAddressSelection> deliveryAddressesToReplace,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Selected delivery options used during discount validation.")
        List<@Valid DeliveryOptionSelection> selectedDeliveryOptions
) {

    @AssertTrue(message = "merchantId or merchantDomain is required")
    public boolean hasMerchantIdentity() {
        return merchantId != null || (merchantDomain != null && !merchantDomain.isBlank());
    }

    @Schema(name = "SearchDiscountCodesItemRequest", description = "Product variant and quantity used for discount validation.")
    public record Item(
            @NotBlank
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Remote product variant id.", example = "gid://shopify/ProductVariant/1")
            String productVariantId,
            @NotNull
            @Positive
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Quantity of this product variant.", example = "1")
            Integer quantity
    ) {
    }

    @Schema(name = "SearchDiscountCodesBuyerIdentityRequest", description = "Buyer identity fields accepted by cart providers.")
    public record BuyerIdentity(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer email address.", example = "buyer@example.com")
            String email,
            @JsonAlias({"phone", "phone_number"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer phone number.", example = "+14155552671")
            String phoneNumber,
            @JsonAlias("first_name")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer first name.", example = "Ada")
            String firstName,
            @JsonAlias("last_name")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer last name.", example = "Lovelace")
            String lastName,
            @JsonAlias({"country", "country_code"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer country code.", example = "US")
            String countryCode
    ) {
    }

    @Schema(name = "SearchDiscountCodesDeliveryAddressSelectionRequest", description = "Delivery address selection accepted by cart providers.")
    public record DeliveryAddressSelection(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Existing delivery destination id.", example = "delivery-destination-1")
            String id,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Whether this address should be selected.")
            Boolean selected,
            @Valid
            @JsonAlias("delivery_address")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Nested delivery address. Flat address fields are also accepted for compatibility.")
            DeliveryAddress deliveryAddress,
            @JsonAlias("first_name")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Recipient first name.", example = "Ada")
            String firstName,
            @JsonAlias("last_name")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Recipient last name.", example = "Lovelace")
            String lastName,
            @JsonAlias({"phone", "phone_number"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Recipient phone number.", example = "+14155552671")
            String phoneNumber,
            @JsonAlias({"address1", "street_address"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Street address.", example = "1 Market St")
            String streetAddress,
            @JsonAlias({"address2", "extended_address"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Apartment, suite, or other address detail.", example = "Suite 200")
            String extendedAddress,
            @JsonAlias("address_locality")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "City or locality.", example = "San Francisco")
            String city,
            @JsonAlias({"province", "province_code", "address_region"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Province, state, or region code.", example = "CA")
            String provinceCode,
            @JsonAlias({"zip", "postal_code"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Postal code.", example = "94105")
            String postalCode,
            @JsonAlias({"country", "country_code", "address_country"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Country code.", example = "US")
            String countryCode
    ) {
    }

    @Schema(name = "SearchDiscountCodesDeliveryAddressRequest", description = "Delivery address fields accepted by cart providers.")
    public record DeliveryAddress(
            @JsonAlias("first_name")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Recipient first name.", example = "Ada")
            String firstName,
            @JsonAlias("last_name")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Recipient last name.", example = "Lovelace")
            String lastName,
            @JsonAlias({"phone", "phone_number"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Recipient phone number.", example = "+14155552671")
            String phoneNumber,
            @JsonAlias({"address1", "street_address"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Street address.", example = "1 Market St")
            String streetAddress,
            @JsonAlias({"address2", "extended_address"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Apartment, suite, or other address detail.", example = "Suite 200")
            String extendedAddress,
            @JsonAlias("address_locality")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "City or locality.", example = "San Francisco")
            String city,
            @JsonAlias({"province", "province_code", "address_region"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Province, state, or region code.", example = "CA")
            String provinceCode,
            @JsonAlias({"zip", "postal_code"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Postal code.", example = "94105")
            String postalCode,
            @JsonAlias({"country", "country_code", "address_country"})
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Country code.", example = "US")
            String countryCode
    ) {
    }

    @Schema(name = "SearchDiscountCodesDeliveryOptionSelectionRequest", description = "Delivery option selection accepted by cart providers.")
    public record DeliveryOptionSelection(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Delivery group id or handle.", example = "delivery-group-1")
            String id,
            @JsonAlias("group_id")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Delivery group id.", example = "delivery-group-1")
            String groupId,
            @JsonAlias("delivery_group_id")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Merchant delivery group id.", example = "delivery-group-1")
            String deliveryGroupId,
            @JsonAlias("option_handle")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Delivery option handle.", example = "standard")
            String optionHandle,
            @JsonAlias("delivery_option_handle")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Merchant delivery option handle.", example = "standard")
            String deliveryOptionHandle,
            @JsonAlias("selected_option_id")
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Selected delivery option id.", example = "standard")
            String selectedOptionId
    ) {
    }
}
