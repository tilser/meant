package com.meant.api.module.merchant.service;

import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.SHOPIFY_SHOP;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.PROVIDER_ID;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.STOREFRONT_DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIntegrationStatus.ACTIVE;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import java.net.IDN;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves buyer-visible merchant origins only through persisted, verified merchant identity.
 *
 * <p>Provider seller domains and transport endpoints are accepted only as lookup coordinates.
 * They are never returned directly.
 */
@Service
@RequiredArgsConstructor
public class MerchantPresentationOriginService {

    private static final String SHOPIFY_PROVIDER = "SHOPIFY";

    private final MerchantRepository merchantRepository;
    private final MerchantIdentityRepository merchantIdentityRepository;
    private final MerchantIntegrationRepository merchantIntegrationRepository;

    @Transactional(readOnly = true)
    public Map<ResultProvenance, String> resolveAll(Collection<ResultProvenance> provenance) {
        List<ResultProvenance> sources = provenance == null
                ? List.of()
                : provenance.stream().filter(source -> source != null).distinct().toList();
        if (sources.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Merchant> byIntegrationId = merchantsByIntegrationId(sources);
        Map<String, Merchant> byShopifyShopId = merchantsByShopifyShopId(sources);
        Map<String, Merchant> byDomain = merchantsByDomain(sources);
        Map<ResultProvenance, String> resolved = new LinkedHashMap<>();
        for (ResultProvenance source : sources) {
            String existing = buyerOrigin(source.merchantOrigin());
            if (existing != null) {
                resolved.put(source, existing);
                continue;
            }
            Merchant merchant = merchant(source, byIntegrationId, byShopifyShopId, byDomain);
            String origin = merchant == null ? null : buyerOrigin(merchant.getDomain());
            if (origin != null) {
                resolved.put(source, origin);
            }
        }
        return Map.copyOf(resolved);
    }

    @Transactional(readOnly = true)
    public String resolve(CatalogProductReference reference) {
        if (reference == null) {
            return null;
        }
        return resolve(
                reference.localMerchantId(),
                reference.localRouting() == null
                        ? null
                        : reference.localRouting().merchantIntegrationId(),
                reference.discoverySource().provider().value(),
                reference.externalMerchantReference() == null
                        ? null
                        : reference.externalMerchantReference().value(),
                reference.externalMerchantDomain()
        );
    }

    @Transactional(readOnly = true)
    public String resolve(
            UUID localMerchantId,
            UUID merchantIntegrationId,
            String provider,
            String externalMerchantId,
            String routingDomain
    ) {
        Optional<Merchant> merchant = localMerchantId == null
                ? Optional.empty()
                : merchantRepository.findByIdAndActiveTrue(localMerchantId);
        if (merchant.isEmpty() && merchantIntegrationId != null) {
            merchant = merchantIntegrationRepository.findById(merchantIntegrationId)
                    .filter(integration -> integration.getStatus() == ACTIVE)
                    .map(MerchantIntegration::getMerchant)
                    .filter(Merchant::isActive);
        }
        if (merchant.isEmpty() && SHOPIFY_PROVIDER.equalsIgnoreCase(provider) && hasText(externalMerchantId)) {
            merchant = merchantRepository.findActiveByIdentity(
                    SHOPIFY_SHOP,
                    externalMerchantId.trim().toLowerCase(Locale.ROOT)
            );
        }
        String normalizedDomain = normalizedDomain(routingDomain);
        if (merchant.isEmpty() && normalizedDomain != null) {
            merchant = merchantRepository.findByDomainAndActiveTrue(normalizedDomain)
                    .or(() -> merchantRepository.findActiveByIdentity(DOMAIN, normalizedDomain));
        }
        return merchant.map(Merchant::getDomain)
                .map(MerchantPresentationOriginService::buyerOrigin)
                .orElse(null);
    }

    private Map<UUID, Merchant> merchantsByIntegrationId(List<ResultProvenance> sources) {
        Set<UUID> integrationIds = sources.stream()
                .filter(source -> source.localRouting() != null)
                .map(source -> source.localRouting().merchantIntegrationId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (integrationIds.isEmpty()) {
            return Map.of();
        }
        return merchantIntegrationRepository.findByIdInOrderByCreatedAtAsc(integrationIds).stream()
                .filter(integration -> integration.getStatus() == ACTIVE)
                .filter(integration -> integration.getMerchant().isActive())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        MerchantIntegration::getId,
                        MerchantIntegration::getMerchant,
                        (left, right) -> left
                ));
    }

    private Map<String, Merchant> merchantsByShopifyShopId(List<ResultProvenance> sources) {
        Set<String> values = sources.stream()
                .filter(source -> SHOPIFY_PROVIDER.equals(source.provider().value()))
                .filter(source -> source.externalMerchantReference() != null)
                .map(source -> source.externalMerchantReference().value())
                .filter(MerchantPresentationOriginService::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return merchantsByIdentity(SHOPIFY_SHOP, PROVIDER_ID, values);
    }

    private Map<String, Merchant> merchantsByDomain(List<ResultProvenance> sources) {
        Set<String> domains = sources.stream()
                .map(ResultProvenance::externalMerchantDomain)
                .map(MerchantPresentationOriginService::normalizedDomain)
                .filter(value -> value != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (domains.isEmpty()) {
            return Map.of();
        }
        Map<String, Merchant> resolved = new LinkedHashMap<>();
        merchantRepository.findByDomainIn(domains).stream()
                .filter(Merchant::isActive)
                .forEach(merchant -> resolved.put(normalizedDomain(merchant.getDomain()), merchant));
        merchantsByIdentity(DOMAIN, STOREFRONT_DOMAIN, domains).forEach(resolved::putIfAbsent);
        return Map.copyOf(resolved);
    }

    private Map<String, Merchant> merchantsByIdentity(
            com.meant.api.module.merchant.constant.MerchantIdentityNamespace namespace,
            com.meant.api.module.merchant.constant.MerchantIdentityRole role,
            Set<String> values
    ) {
        if (values.isEmpty()) {
            return Map.of();
        }
        return merchantIdentityRepository.findByNamespaceInAndNormalizedValueIn(Set.of(namespace), values).stream()
                .filter(identity -> identity.getNamespace() == namespace && identity.getRole() == role)
                .filter(identity -> identity.getMerchant().isActive())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        MerchantIdentity::getNormalizedValue,
                        MerchantIdentity::getMerchant,
                        (left, right) -> left
                ));
    }

    private Merchant merchant(
            ResultProvenance source,
            Map<UUID, Merchant> byIntegrationId,
            Map<String, Merchant> byShopifyShopId,
            Map<String, Merchant> byDomain
    ) {
        if (source.localRouting() != null) {
            Merchant local = byIntegrationId.get(source.localRouting().merchantIntegrationId());
            if (local != null) {
                return local;
            }
        }
        if (SHOPIFY_PROVIDER.equals(source.provider().value())
                && source.externalMerchantReference() != null) {
            Merchant shopify = byShopifyShopId.get(
                    source.externalMerchantReference().value().trim().toLowerCase(Locale.ROOT));
            if (shopify != null) {
                return shopify;
            }
        }
        String domain = normalizedDomain(source.externalMerchantDomain());
        return domain == null ? null : byDomain.get(domain);
    }

    private static String normalizedDomain(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            String normalized = IDN.toASCII(value.trim(), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            while (normalized.endsWith(".")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String buyerOrigin(String value) {
        return MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
