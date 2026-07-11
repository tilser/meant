package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByIdsQuery;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Resolves client hints to an active, merchant-owned server integration or rejects them. */
@Component
public class GenericUcpCatalogReferenceVerifier {
    private final MerchantIntegrationLookupService integrationLookupService;

    public GenericUcpCatalogReferenceVerifier(MerchantIntegrationLookupService integrationLookupService) {
        this.integrationLookupService = integrationLookupService;
    }

    public Map<CatalogProductReference, MerchantIntegrationResult> verify(List<CatalogProductReference> references) {
        Set<UUID> merchantIds = references.stream()
                .map(CatalogProductReference::localMerchantId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> integrationIds = references.stream()
                .filter(reference -> reference.localMerchantId() == null)
                .map(CatalogProductReference::localRouting)
                .filter(java.util.Objects::nonNull)
                .map(LocalMerchantRouting::merchantIntegrationId)
                .collect(Collectors.toSet());
        if (merchantIds.isEmpty() && integrationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MerchantIntegrationResult> integrationsById = new LinkedHashMap<>();
        if (!merchantIds.isEmpty()) {
            integrationLookupService.listByMerchants(new ListMerchantIntegrationsByMerchantsQuery(merchantIds))
                    .forEach(integration -> integrationsById.put(integration.id(), integration));
        }
        if (!integrationIds.isEmpty()) {
            integrationLookupService.listByIds(new ListMerchantIntegrationsByIdsQuery(integrationIds))
                    .forEach(integration -> integrationsById.put(integration.id(), integration));
        }
        List<MerchantIntegrationResult> integrations = List.copyOf(integrationsById.values());
        Map<UUID, MerchantIntegrationResult> byId = integrations.stream()
                .filter(this::eligible)
                .collect(Collectors.toMap(MerchantIntegrationResult::id, value -> value));
        Map<UUID, List<MerchantIntegrationResult>> byMerchant = integrations.stream()
                .filter(this::eligible)
                .collect(Collectors.groupingBy(MerchantIntegrationResult::merchantId));
        Map<CatalogProductReference, MerchantIntegrationResult> verified = new LinkedHashMap<>();
        for (CatalogProductReference reference : references) {
            MerchantIntegrationResult integration = resolve(reference, byMerchant, byId);
            if (integration != null) {
                verified.put(reference, integration);
            }
        }
        return Map.copyOf(verified);
    }

    public CatalogProductReference canonical(
            CatalogProductReference requested,
            MerchantIntegrationResult integration,
            ExternalIdentifier product,
            ExternalIdentifier variant,
            List<com.meant.api.module.catalog.service.dto.ProductAttribute> options
    ) {
        ExternalIdentifier merchant = merchantIdentity(integration);
        return new CatalogProductReference(
                requested.interactionKey(),
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                integration.merchantId(),
                new LocalMerchantRouting(integration.id()),
                merchant,
                product,
                variant,
                options
        );
    }

    private MerchantIntegrationResult resolve(
            CatalogProductReference reference,
            Map<UUID, List<MerchantIntegrationResult>> byMerchant,
            Map<UUID, MerchantIntegrationResult> byId
    ) {
        if (!MerchantCatalogSourceIdentity.DISCOVERY_SOURCE.equals(reference.discoverySource())) {
            return null;
        }
        List<MerchantIntegrationResult> candidates = reference.localMerchantId() == null
                ? java.util.Optional.ofNullable(reference.localRouting())
                        .map(LocalMerchantRouting::merchantIntegrationId)
                        .map(byId::get)
                        .map(List::of)
                        .orElseGet(List::of)
                : byMerchant.getOrDefault(reference.localMerchantId(), List.of());
        return candidates.stream()
                .filter(integration -> routingMatches(reference, integration))
                .filter(integration -> merchantMatches(reference.externalMerchantReference(), integration))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        matches -> matches.size() == 1 ? matches.getFirst() : null));
    }

    private boolean eligible(MerchantIntegrationResult integration) {
        return integration.provider() == MerchantIntegrationProvider.GENERIC_UCP
                && integration.status() == MerchantIntegrationStatus.ACTIVE
                && (integration.roles().contains(MerchantIntegrationRole.STOREFRONT_CATALOG)
                        || integration.roles().contains(MerchantIntegrationRole.CATALOG_PROVENANCE));
    }

    private boolean routingMatches(CatalogProductReference reference, MerchantIntegrationResult integration) {
        return reference.localRouting() == null
                || reference.localRouting().merchantIntegrationId().equals(integration.id());
    }

    private boolean merchantMatches(ExternalIdentifier requested, MerchantIntegrationResult integration) {
        return requested == null || requested.equals(merchantIdentity(integration));
    }

    private ExternalIdentifier merchantIdentity(MerchantIntegrationResult integration) {
        String value = firstText(integration.externalMerchantId(), integration.verifiedShopIdentity());
        return ExternalIdentifier.optional(
                ExternalIdentifierType.MERCHANT,
                MerchantCatalogSourceIdentity.PROVIDER.value(),
                value
        );
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second == null ? null : second.trim();
    }
}
