package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.RehydratedCommercialFacts;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Maps verified identifiers and ephemeral rehydration facts without reading legacy payload columns. */
@Component
public class UserSavedProductResultMapper {
    private static final TypeReference<List<ProductAttribute>> OPTIONS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public UserSavedProductResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CatalogProductReference reference(UserSavedProduct entity) {
        try {
            ProviderIdentity provider = new ProviderIdentity(entity.getSourceProvider());
            return new CatalogProductReference(
                    entity.getProductKey(),
                    new DiscoverySourceIdentity(
                            provider,
                            ResultSourceType.valueOf(entity.getSourceType()),
                            entity.getSourceIdentity()
                    ),
                    entity.getLocalMerchantId(),
                    entity.getMerchantIntegrationId() == null
                            ? null
                            : new LocalMerchantRouting(entity.getMerchantIntegrationId()),
                    ExternalIdentifier.optional(
                            ExternalIdentifierType.MERCHANT,
                            provider.value(),
                            entity.getExternalMerchantId()
                    ),
                    new ExternalIdentifier(
                            ExternalIdentifierType.PRODUCT,
                            provider.value(),
                            entity.getExternalProductId()
                    ),
                    ExternalIdentifier.optional(
                            ExternalIdentifierType.VARIANT,
                            provider.value(),
                            entity.getExternalVariantId()
                    ),
                    options(entity.getSelectedOptionsJson())
            );
        } catch (IllegalArgumentException | JacksonException exception) {
            return null;
        }
    }

    public UserSavedProductResult result(
            UserSavedProduct entity,
            CatalogProductRehydrationResult rehydrated
    ) {
        RehydratedCommercialFacts facts = rehydrated != null
                && rehydrated.status() == CatalogRehydrationStatus.FRESH
                ? rehydrated.facts()
                : null;
        CatalogProductReference resolved = facts == null ? null : rehydrated.resolvedReference();
        Double price = facts == null || facts.price() == null ? null : facts.price().minorUnits() / 100.0d;
        Boolean available = availability(facts);
        List<UserSavedProductResult.Offer> offers = facts == null ? List.of() : List.of(
                new UserSavedProductResult.Offer(
                        resolved.externalMerchantReference() == null
                                ? null
                                : resolved.externalMerchantReference().value(),
                        price,
                        null,
                        resolved.localMerchantId() == null ? null : resolved.localMerchantId().toString(),
                        null,
                        resolved.externalVariantReference() == null
                                ? null
                                : resolved.externalVariantReference().value(),
                        null,
                        available
                )
        );
        return new UserSavedProductResult(
                entity.getProductKey(),
                null,
                facts == null ? null : facts.title(),
                null,
                null,
                null,
                image(facts),
                null,
                facts == null ? null : true,
                null,
                price,
                facts == null ? null : 1,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                offers,
                null,
                List.of(),
                facts != null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Boolean availability(RehydratedCommercialFacts facts) {
        if (facts == null || facts.availability().status() == OfferAvailabilityStatus.UNKNOWN) {
            return null;
        }
        return facts.availability().status() != OfferAvailabilityStatus.OUT_OF_STOCK
                && facts.availability().status() != OfferAvailabilityStatus.DISCONTINUED;
    }

    private String image(RehydratedCommercialFacts facts) {
        if (facts == null) {
            return null;
        }
        return facts.sourceMedia().stream()
                .filter(media -> media.type() == ProductMediaType.IMAGE)
                .map(media -> media.url().toString())
                .findFirst()
                .orElse(null);
    }

    private List<ProductAttribute> options(String value) throws JacksonException {
        List<ProductAttribute> options = objectMapper.readValue(value, OPTIONS_TYPE);
        return options == null ? List.of() : options;
    }
}
