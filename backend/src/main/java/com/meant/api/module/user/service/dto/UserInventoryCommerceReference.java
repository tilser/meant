package com.meant.api.module.user.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Provider-neutral commerce identity retained for an inventory purchase. */
public record UserInventoryCommerceReference(
        @NotBlank @Size(max = 100) String provider,
        UUID merchantIntegrationId,
        @Size(max = 500) String externalMerchantId,
        @Size(max = 500) String externalMerchantDomain,
        @Size(max = 500) String canonicalProductKey,
        @Size(max = 500) String offerKey,
        @NotBlank @Size(max = 100) String sourceType,
        @NotBlank @Size(max = 500) String sourceIdentity,
        @NotBlank @Size(max = 500) String externalProductId,
        @Size(max = 500) String externalVariantId,
        List<@Valid UserInventorySelectedOption> selectedOptions
) {
    public UserInventoryCommerceReference {
        provider = provider == null ? null : provider.trim().toUpperCase(Locale.ROOT);
        externalMerchantId = trimToNull(externalMerchantId);
        externalMerchantDomain = normalizeDomain(externalMerchantDomain);
        canonicalProductKey = trimToNull(canonicalProductKey);
        offerKey = trimToNull(offerKey);
        sourceType = sourceType == null ? null : sourceType.trim().toUpperCase(Locale.ROOT);
        sourceIdentity = sourceIdentity == null ? null : sourceIdentity.trim();
        externalProductId = externalProductId == null ? null : externalProductId.trim();
        externalVariantId = trimToNull(externalVariantId);
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
    }

    private static String normalizeDomain(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
