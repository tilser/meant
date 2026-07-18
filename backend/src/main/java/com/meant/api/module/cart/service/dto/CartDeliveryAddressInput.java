package com.meant.api.module.cart.service.dto;

public record CartDeliveryAddressInput(
        String firstName,
        String lastName,
        String phoneNumber,
        String streetAddress,
        String extendedAddress,
        String addressLocality,
        String addressRegion,
        String postalCode,
        String addressCountry
) {
}
