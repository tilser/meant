package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import com.meant.api.module.user.service.query.RehydrateUserInventoryProductQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Rehydrates purchased inventory through provider-neutral catalog boundaries. */
@Service
@Validated
@RequiredArgsConstructor
public class UserInventoryProductRehydrationService {
    private static final TypeReference<List<UserInventorySelectedOption>> SELECTED_OPTION_LIST_TYPE =
            new TypeReference<>() {
            };

    private final UserInventoryItemRepository repository;
    private final CatalogProductRehydrationService catalogProductRehydrationService;
    private final ObjectMapper objectMapper;

    public UserInventoryProductRehydrationResult rehydrate(
            @NotNull @Valid RehydrateUserInventoryProductQuery query
    ) {
        UserInventoryItem item = repository.findByIdAndUserId(query.inventoryItemId(), query.userId())
                .orElseThrow(() -> UserException.notFound(
                        "Inventory item not found: " + query.inventoryItemId()));
        UserInventoryCommerceReference commerceReference = commerceReference(item);
        if (commerceReference == null) {
            return fallback(item, null);
        }
        CatalogProductReference catalogReference;
        try {
            catalogReference = catalogReference(item, commerceReference);
        } catch (IllegalArgumentException exception) {
            return fallback(item, commerceReference);
        }
        return new UserInventoryProductRehydrationResult(
                item.getId(),
                commerceReference,
                catalogProductRehydrationService.rehydrate(
                        catalogReference,
                        new CatalogRehydrationContext(query.countryCode(), null)
                ),
                item.getName(),
                item.getCategory(),
                item.getPhotoPath(),
                item.getSize(),
                item.getColor(),
                item.getMaterial(),
                item.getPurchasedOn()
        );
    }

    private UserInventoryProductRehydrationResult fallback(
            UserInventoryItem item,
            UserInventoryCommerceReference commerceReference
    ) {
        return new UserInventoryProductRehydrationResult(
                item.getId(), commerceReference, null, item.getName(), item.getCategory(),
                item.getPhotoPath(), item.getSize(), item.getColor(), item.getMaterial(), item.getPurchasedOn());
    }

    private CatalogProductReference catalogReference(
            UserInventoryItem item,
            UserInventoryCommerceReference reference
    ) {
        ProviderIdentity provider = new ProviderIdentity(reference.provider());
        return new CatalogProductReference(
                firstText(reference.offerKey(), reference.canonicalProductKey(), "inventory:" + item.getId()),
                new DiscoverySourceIdentity(
                        provider, ResultSourceType.valueOf(reference.sourceType()), reference.sourceIdentity()),
                null,
                reference.merchantIntegrationId() == null
                        ? null : new LocalMerchantRouting(reference.merchantIntegrationId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.MERCHANT, provider.value(), reference.externalMerchantId()),
                reference.externalMerchantDomain(),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT, provider.value(), reference.externalProductId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.VARIANT, provider.value(), reference.externalVariantId()),
                reference.selectedOptions().stream()
                        .map(option -> new ProductAttribute(option.group(), option.name(), option.value()))
                        .toList(),
                List.of(),
                null
        );
    }

    private UserInventoryCommerceReference commerceReference(UserInventoryItem item) {
        if (!hasText(item.getProvider())
                || !hasText(item.getSourceType())
                || !hasText(item.getSourceIdentity())
                || !hasText(item.getExternalProductId())) {
            return null;
        }
        try {
            ResultSourceType.valueOf(item.getSourceType().trim().toUpperCase(java.util.Locale.ROOT));
            return new UserInventoryCommerceReference(
                    item.getProvider(),
                    item.getMerchantIntegrationId(),
                    item.getExternalMerchantId(),
                    item.getExternalMerchantDomain(),
                    item.getMerchantOrigin(),
                    item.getCanonicalProductKey(),
                    item.getOfferKey(),
                    item.getSourceType(),
                    item.getSourceIdentity(),
                    item.getExternalProductId(),
                    item.getExternalVariantId(),
                    selectedOptions(item.getSelectedOptionsJson())
            );
        } catch (IllegalArgumentException | UserException | NullPointerException exception) {
            return null;
        }
    }

    private List<UserInventorySelectedOption> selectedOptions(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, SELECTED_OPTION_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new UserException("Could not parse inventory selected options", exception);
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        throw new IllegalArgumentException("At least one inventory interaction key is required");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
