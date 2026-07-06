package com.meant.api.module.cart.service.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckoutConsentResult(
        UUID buyerConsentId,
        Instant expiresAt
) {
}
