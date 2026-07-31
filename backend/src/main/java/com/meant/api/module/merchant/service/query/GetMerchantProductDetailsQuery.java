package com.meant.api.module.merchant.service.query;

import com.meant.api.module.catalog.service.dto.ProductAttribute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record GetMerchantProductDetailsQuery(
        @NotNull
        UUID merchantId,

        @NotBlank
        String productId,

        String addressCountry,

        String language,

        String currency,

        @NotNull
        @Size(max = 20)
        List<ProductAttribute> selectedOptions,

        @NotNull
        @Size(max = 20)
        List<@NotBlank String> preferences,

        boolean selectionRequest
) {
    public GetMerchantProductDetailsQuery {
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        preferences = preferences == null
                ? List.of()
                : preferences.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
    }

    public GetMerchantProductDetailsQuery(
            UUID merchantId,
            String productId,
            String addressCountry,
            String language,
            List<ProductAttribute> selectedOptions,
            List<String> preferences
    ) {
        this(merchantId, productId, addressCountry, language, null, selectedOptions, preferences, true);
    }

    public GetMerchantProductDetailsQuery(
            UUID merchantId,
            String productId,
            String addressCountry,
            String language,
            String currency,
            List<ProductAttribute> selectedOptions,
            List<String> preferences
    ) {
        this(merchantId, productId, addressCountry, language, currency, selectedOptions, preferences, true);
    }

    public GetMerchantProductDetailsQuery(
            UUID merchantId,
            String productId,
            String addressCountry,
            String language
    ) {
        this(merchantId, productId, addressCountry, language, null, List.of(), List.of(), false);
    }

    public GetMerchantProductDetailsQuery(
            UUID merchantId,
            String productId,
            String addressCountry,
            String language,
            String currency
    ) {
        this(merchantId, productId, addressCountry, language, currency, List.of(), List.of(), false);
    }
}
