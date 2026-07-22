package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.entity.UserCheckoutDetails;
import java.time.Instant;
import java.util.UUID;

public record UserCheckoutDetailsResult(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String streetAddress,
        String extendedAddress,
        String addressLocality,
        String addressRegion,
        String postalCode,
        String addressCountry,
        Instant updatedAt
) {

    public static UserCheckoutDetailsResult from(UserCheckoutDetails details) {
        return new UserCheckoutDetailsResult(
                details.getUserId(),
                details.getEmail(),
                details.getFirstName(),
                details.getLastName(),
                details.getPhoneNumber(),
                details.getStreetAddress(),
                details.getExtendedAddress(),
                details.getAddressLocality(),
                details.getAddressRegion(),
                details.getPostalCode(),
                details.getAddressCountry(),
                details.getUpdatedAt()
        );
    }
}
