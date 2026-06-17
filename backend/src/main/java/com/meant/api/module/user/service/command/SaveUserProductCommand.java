package com.meant.api.module.user.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SaveUserProductCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 500)
        String productKey,

        @Size(max = 500)
        String productHash,

        @NotBlank
        @Size(max = 500)
        String name,

        @NotBlank
        @Size(max = 500)
        String brand,

        @NotBlank
        @Size(max = 200)
        String category,

        @NotBlank
        @Size(max = 100)
        String tone,

        @Size(max = 2048)
        String imageUrl,

        @Size(max = 2048)
        String productUrl,

        @NotNull
        Boolean remote,

        @NotNull
        @Min(0)
        @Max(100)
        Integer matchScore,

        @NotNull
        @PositiveOrZero
        Double priceFrom,

        @NotNull
        @PositiveOrZero
        Integer merchantCount,

        List<@NotBlank @Size(max = 200) String> satisfies,

        List<@NotBlank @Size(max = 200) String> misses,

        @NotBlank
        String note,

        List<@NotBlank String> pros,

        List<@NotBlank String> cons,

        @NotNull
        @Valid
        Review review,

        @NotEmpty
        List<@Valid Offer> offers,

        @Size(max = 100)
        String needs,

        List<@NotBlank @Size(max = 100) String> provides
) {

    public record Review(
            @NotNull
            @PositiveOrZero
            Double score,

            @NotNull
            @PositiveOrZero
            Integer count,

            @NotBlank
            String insight
    ) {
    }

    public record Offer(
            @NotBlank
            @Size(max = 500)
            String merchant,

            @NotNull
            @PositiveOrZero
            Double price,

            @NotBlank
            @Size(max = 500)
            String delivery,

            @Size(max = 255)
            String merchantId,

            @Size(max = 255)
            String merchantDomain,

            @Size(max = 255)
            String productVariantId,

            @Size(max = 255)
            String variantTitle,

            Boolean available
    ) {
    }
}
