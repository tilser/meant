package com.meant.api.module.agent.service;

import com.meant.api.module.agent.service.dto.AgentInventoryProductAnchor;
import com.meant.api.module.agent.service.tool.AgentProductReadToolException;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.service.UserCanonicalProductReferencePersistenceService;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.UserInventoryProductRehydrationService;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;
import com.meant.api.module.user.service.query.RehydrateUserInventoryProductQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentInventoryProductAnchorService {

    private final UserCommerceContextService commerceContextService;
    private final UserInventoryProductRehydrationService rehydrationService;
    private final UserCanonicalProductReferencePersistenceService referencePersistenceService;

    public AgentInventoryProductAnchor anchor(UUID userId, UUID inventoryItemId) {
        String country = commerceContextService.find(userId).countryCode();
        UserInventoryProductRehydrationResult inventory = rehydrationService.rehydrate(
                new RehydrateUserInventoryProductQuery(userId, inventoryItemId, country));
        if (inventory.commerceReference() == null) {
            throw AgentProductReadToolException.invalid(
                    "This inventory item has no verified commerce identity for similarity search.");
        }
        CatalogProductReference reference = inventory.currentFactsAvailable()
                ? inventory.rehydration().resolvedReference()
                : reference(inventory.inventoryItemId(), inventory.commerceReference());
        RehydratedCommercialFacts facts = inventory.currentFactsAvailable()
                ? inventory.rehydration().facts()
                : null;
        String canonicalProductKey = text(
                inventory.commerceReference().canonicalProductKey(),
                "inventory-product:" + inventory.inventoryItemId());
        CanonicalProduct product = product(
                canonicalProductKey,
                inventory.fallbackName(),
                reference,
                facts,
                inventory.commerceReference().merchantOrigin()
        );
        referencePersistenceService.replace(userId, List.of(product));
        return new AgentInventoryProductAnchor(canonicalProductKey, product.title());
    }

    private CanonicalProduct product(
            String canonicalProductKey,
            String fallbackName,
            CatalogProductReference reference,
            RehydratedCommercialFacts facts,
            String merchantOrigin
    ) {
        ResultFreshness freshness = facts == null ? new ResultFreshness(Instant.now(), null) : facts.freshness();
        ResultSourceReference sourceReference = new ResultSourceReference(
                reference.discoverySource().type(), reference.discoverySource().value(), null);
        ResultProvenance provenance = new ResultProvenance(
                reference.discoverySource().provider(),
                reference.discoverySource(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                freshness,
                sourceReference,
                merchantOrigin
        );
        List<ProductAttribute> selectedOptions = facts == null
                ? reference.selectedOptions() : facts.selectedOptions();
        OfferIdentity identity = new OfferIdentity(
                reference.discoverySource().provider(),
                merchantScope(reference),
                reference.externalProductReference(),
                facts != null && facts.selectedVariant() != null
                        ? facts.selectedVariant() : reference.externalVariantReference(),
                selectedOptions,
                reference.components(),
                reference.sellingPlanIdentity()
        );
        Offer offer = new Offer(
                identity,
                facts == null ? null : facts.merchantName(),
                null,
                facts == null ? null : facts.price(),
                null,
                facts == null ? OfferAvailability.unknown() : facts.availability(),
                facts == null ? List.of() : facts.fulfillment(),
                null,
                List.of(provenance)
        );
        String title = facts == null ? fallbackName : text(facts.title(), fallbackName);
        return new CanonicalProduct(
                canonicalProductKey,
                title,
                null,
                facts == null ? List.of() : facts.sourceMedia(),
                selectedOptions,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );
    }

    private OfferMerchantScope merchantScope(CatalogProductReference reference) {
        if (reference.externalMerchantReference() != null) {
            return OfferMerchantScope.external(reference.externalMerchantReference());
        }
        if (reference.localRouting() != null) {
            return OfferMerchantScope.localIntegrationFallback(reference.localRouting().merchantIntegrationId());
        }
        throw AgentProductReadToolException.invalid(
                "This inventory item has no verified merchant scope for similarity search.");
    }

    private CatalogProductReference reference(UUID inventoryItemId, UserInventoryCommerceReference value) {
        ProviderIdentity provider = new ProviderIdentity(value.provider());
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.valueOf(value.sourceType()), value.sourceIdentity());
        return new CatalogProductReference(
                text(value.canonicalProductKey(), value.offerKey(), "inventory-product:" + inventoryItemId),
                source,
                null,
                value.merchantIntegrationId() == null ? null : new LocalMerchantRouting(value.merchantIntegrationId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.MERCHANT, provider.value(), value.externalMerchantId()),
                value.externalMerchantDomain(),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT, provider.value(), value.externalProductId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.VARIANT, provider.value(), value.externalVariantId()),
                value.selectedOptions().stream()
                        .map(option -> new ProductAttribute(option.group(), option.name(), option.value()))
                        .toList(),
                List.of(),
                null
        );
    }

    private String text(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
