package com.meant.api.module.cart.service.dto;

public record CartBuyerIdentityInput(
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        String countryCode
) {
}
