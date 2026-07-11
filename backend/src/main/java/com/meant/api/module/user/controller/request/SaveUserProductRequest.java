package com.meant.api.module.user.controller.request;

import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
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

public record SaveUserProductRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String id,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String productHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String brand,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 100)
        String tone,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2048)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2048)
        String productUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean remote,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(0)
        @Max(100)
        Integer match,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @PositiveOrZero
        Double priceFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @PositiveOrZero
        Integer merchants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank @Size(max = 200) String> satisfies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank @Size(max = 200) String> misses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String note,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> pros,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> cons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Valid
        Review review,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        List<@Valid Offer> offers,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 100)
        String needs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank @Size(max = 100) String> provides,
        @Schema(
                description = "Provider identifiers for session-only results; omitted only when the server can resolve an admitted cache row",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Valid
        CatalogReference catalogReference
) {

    public record CatalogReference(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String provider,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ResultSourceType sourceType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String sourceIdentity,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            UUID localMerchantId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            UUID merchantIntegrationId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 512) String externalMerchantId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 512) String externalProductId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 512) String externalVariantId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<@Valid SelectedOption> selectedOptions
    ) {
    }

    public record SelectedOption(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 100) String group,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 500) String value
    ) {
    }

    public record Review(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @PositiveOrZero
            Double score,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @PositiveOrZero
            Integer count,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String insight
    ) {
    }

    public record Offer(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Size(max = 500)
            String merchant,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @PositiveOrZero
            Double price,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Size(max = 500)
            String delivery,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 255)
            String merchantId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 255)
            String merchantDomain,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 255)
            String productVariantId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 255)
            String variantTitle,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean available
    ) {
    }
}
