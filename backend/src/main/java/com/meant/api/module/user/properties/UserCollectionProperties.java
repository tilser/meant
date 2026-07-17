package com.meant.api.module.user.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user.collections")
public record UserCollectionProperties(

        @NotNull
        @Valid
        SavedProducts savedProducts,

        @NotNull
        @Valid
        Inventory inventory
) {

    public record SavedProducts(
            @Positive
            int defaultLimit,

            @Positive
            int maxLimit,

            @Positive
            int quota,

            @Positive
            int discoveryLimit
    ) {

        @AssertTrue(message = "saved product default limit must be less than or equal to the max limit")
        public boolean isDefaultLimitValid() {
            return defaultLimit <= maxLimit;
        }

        @AssertTrue(message = "saved product discovery limit must be less than or equal to the quota")
        public boolean isDiscoveryLimitValid() {
            return discoveryLimit <= quota;
        }
    }

    public record Inventory(
            @Positive
            int defaultLimit,

            @Positive
            int maxLimit,

            @Positive
            int quota
    ) {

        @AssertTrue(message = "inventory default limit must be less than or equal to the max limit")
        public boolean isDefaultLimitValid() {
            return defaultLimit <= maxLimit;
        }
    }
}
