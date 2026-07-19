package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "An item owned by the authenticated user.")
public record UserInventoryItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserInventorySource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceProductKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String brand,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserInventoryCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String photoUrl,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String photoPath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String location,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String notes,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String size,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String color,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String material,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> attributes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean consumable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean restockEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer restockThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant purchasedAt,
        @Schema(types = {"string", "null"}, format = "date", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        LocalDate purchasedOn,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CommerceReference commerceReference,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID sourceCheckoutAttemptId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static UserInventoryItemResponse from(UserInventoryItemResult result) {
        return new UserInventoryItemResponse(
                result.id(),
                result.source(),
                result.sourceProductKey(),
                result.productHash(),
                result.name(),
                result.brand(),
                result.category(),
                result.description(),
                result.imageUrl(),
                result.productUrl(),
                result.photoUrl(),
                result.photoPath(),
                result.quantity(),
                result.unit(),
                result.location(),
                result.notes(),
                result.size(),
                result.color(),
                result.material(),
                result.attributes(),
                result.consumable(),
                result.restockEnabled(),
                result.restockThreshold(),
                result.purchasedAt(),
                result.purchasedOn(),
                CommerceReference.from(result.commerceReference()),
                result.sourceCheckoutAttemptId(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    @Schema(
            name = "UserInventoryCommerceReferenceResponse",
            description = "Provider-neutral commerce identity retained for a purchased inventory item."
    )
    public record CommerceReference(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String provider,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            UUID merchantIntegrationId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String externalMerchantId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String externalMerchantDomain,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String canonicalProductKey,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String offerKey,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String sourceType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String sourceIdentity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String externalProductId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String externalVariantId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<SelectedOption> selectedOptions
    ) {
        static CommerceReference from(UserInventoryCommerceReference reference) {
            if (reference == null) {
                return null;
            }
            return new CommerceReference(
                    reference.provider(),
                    reference.merchantIntegrationId(),
                    reference.externalMerchantId(),
                    reference.externalMerchantDomain(),
                    reference.canonicalProductKey(),
                    reference.offerKey(),
                    reference.sourceType(),
                    reference.sourceIdentity(),
                    reference.externalProductId(),
                    reference.externalVariantId(),
                    reference.selectedOptions().stream().map(SelectedOption::from).toList()
            );
        }
    }

    @Schema(
            name = "UserInventorySelectedOptionResponse",
            description = "Exact option selected for the purchased product variant."
    )
    public record SelectedOption(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String group,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {
        static SelectedOption from(UserInventorySelectedOption option) {
            return new SelectedOption(option.group(), option.name(), option.value());
        }
    }
}
