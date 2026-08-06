package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.OfferRankingEvidence;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.support.OfferIdentityStrategy;
import com.meant.api.module.user.entity.UserCanonicalProductReference;
import com.meant.api.module.user.entity.UserCanonicalProductReference.DurableReferenceSnapshot;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserCanonicalProductReferenceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Persists only policy-approved provider identifiers needed to reopen historical product details. */
@Service
@RequiredArgsConstructor
public class UserCanonicalProductReferencePersistenceService {
    private static final TypeReference<List<ProductAttribute>> OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<OfferComponentIdentity>> COMPONENTS_TYPE = new TypeReference<>() {
    };

    private final UserCanonicalProductReferenceRepository repository;
    private final CatalogDataUsePolicyResolver dataUsePolicyResolver;
    private final ObjectMapper objectMapper;
    private final List<OfferIdentityStrategy> offerIdentityStrategies;

    @Transactional
    public void replace(UUID userId, List<CanonicalProduct> products) {
        Map<String, CanonicalProduct> latestProductsByKey = new LinkedHashMap<>();
        safeProducts(products).forEach(product -> latestProductsByKey.put(product.key(), product));
        if (latestProductsByKey.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        repository.deleteByUserIdAndCanonicalProductKeyIn(
                userId,
                List.copyOf(latestProductsByKey.keySet())
        );
        List<UserCanonicalProductReference> replacements = latestProductsByKey.values().stream()
                .flatMap(product -> references(userId, product, now).stream())
                .toList();
        if (!replacements.isEmpty()) {
            repository.saveAll(replacements);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CanonicalProduct> findProduct(UUID userId, String canonicalProductKey) {
        List<UserCanonicalProductReference> stored =
                repository.findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(
                        userId, canonicalProductKey);
        return product(canonicalProductKey, stored);
    }

    @Transactional(readOnly = true)
    public Optional<CanonicalProduct> findProductByOffer(UUID userId, String offerKey) {
        if (offerKey == null || offerKey.isBlank()) {
            return Optional.empty();
        }
        List<UserCanonicalProductReference> stored =
                repository.findByUserIdAndOfferKeyOrderByReferenceVerifiedAtDescIdAsc(
                        userId, offerKey.trim());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        String canonicalProductKey = stored.getFirst().getCanonicalProductKey();
        return product(
                canonicalProductKey,
                stored.stream()
                        .filter(reference -> canonicalProductKey.equals(reference.getCanonicalProductKey()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, CanonicalProduct> findProducts(UUID userId, List<String> canonicalProductKeys) {
        Set<String> requestedKeys = canonicalProductKeys == null
                ? Set.of()
                : canonicalProductKeys.stream()
                        .filter(key -> key != null && !key.isBlank())
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, List<UserCanonicalProductReference>> storedByKey =
                repository.findByUserIdAndCanonicalProductKeyInOrderByCanonicalProductKeyAscOfferRankAscIdAsc(
                                userId, List.copyOf(requestedKeys))
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                UserCanonicalProductReference::getCanonicalProductKey,
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()
                        ));
        Map<String, CanonicalProduct> products = new LinkedHashMap<>();
        for (String canonicalProductKey : requestedKeys) {
            product(canonicalProductKey, storedByKey.getOrDefault(canonicalProductKey, List.of()))
                    .ifPresent(product -> products.put(canonicalProductKey, product));
        }
        return Collections.unmodifiableMap(products);
    }

    int copyReferences(UUID sourceUserId, UUID targetUserId, List<String> canonicalProductKeys) {
        if (sourceUserId == null || targetUserId == null || sourceUserId.equals(targetUserId)
                || canonicalProductKeys == null || canonicalProductKeys.isEmpty()) {
            return 0;
        }
        List<UserCanonicalProductReference> source = repository
                .findByUserIdAndCanonicalProductKeyInOrderByCanonicalProductKeyAscOfferRankAscIdAsc(
                        sourceUserId, canonicalProductKeys);
        if (source.isEmpty()) {
            return 0;
        }
        List<UserCanonicalProductReference> existing = repository
                .findByUserIdAndCanonicalProductKeyInOrderByCanonicalProductKeyAscOfferRankAscIdAsc(
                        targetUserId, canonicalProductKeys);
        Instant now = Instant.now();
        List<UserCanonicalProductReference> known = new ArrayList<>(existing);
        List<UserCanonicalProductReference> copies = new ArrayList<>();
        for (UserCanonicalProductReference reference : source) {
            if (observation(reference) == null
                    || known.stream().anyMatch(reference::hasSameReference)) {
                continue;
            }
            UserCanonicalProductReference copy = reference.copyForUser(targetUserId, now);
            copies.add(copy);
            known.add(copy);
        }
        if (!copies.isEmpty()) {
            repository.saveAll(copies);
        }
        return copies.size();
    }

    private Optional<CanonicalProduct> product(
            String canonicalProductKey,
            List<UserCanonicalProductReference> stored
    ) {
        Map<String, List<DurableObservation>> observationsByOffer = new LinkedHashMap<>();
        for (UserCanonicalProductReference entity : stored) {
            DurableObservation observation = observation(entity);
            if (observation != null) {
                observationsByOffer.computeIfAbsent(entity.getOfferKey(), ignored -> new ArrayList<>())
                        .add(observation);
            }
        }
        List<Offer> offers = observationsByOffer.entrySet().stream()
                .map(entry -> offer(entry.getKey(), entry.getValue()))
                .flatMap(Optional::stream)
                .toList();
        if (offers.isEmpty()) {
            return Optional.empty();
        }
        List<ResultProvenance> provenance = offers.stream()
                .flatMap(offer -> offer.provenance().stream())
                .distinct()
                .toList();
        return Optional.of(new CanonicalProduct(
                canonicalProductKey,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                provenance,
                offers
        ));
    }

    private List<UserCanonicalProductReference> references(
            UUID userId,
            CanonicalProduct product,
            Instant now
    ) {
        List<UserCanonicalProductReference> stored = new ArrayList<>();
        Set<CatalogProductReference> seen = new LinkedHashSet<>();
        for (int offerRank = 0; offerRank < product.offers().size(); offerRank++) {
            Offer offer = product.offers().get(offerRank);
            for (ResultProvenance provenance : offer.provenance()) {
                CatalogRetentionDecision policy = dataUsePolicyResolver.resolve(
                        provenance.discoverySource(), CatalogPayloadClass.IDENTIFIERS_PROVENANCE);
                if (policy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY) {
                    continue;
                }
                CatalogProductReference reference = reference(product.key(), offer, provenance);
                if (!durableAnchor(reference) || !seen.add(reference)) {
                    continue;
                }
                stored.add(UserCanonicalProductReference.create(
                        userId,
                        product.key(),
                        offer.key(),
                        offerRank,
                        snapshot(reference, policy.policyKey()),
                        now
                ));
            }
        }
        return List.copyOf(stored);
    }

    private CatalogProductReference reference(
            String canonicalProductKey,
            Offer offer,
            ResultProvenance provenance
    ) {
        return new CatalogProductReference(
                canonicalProductKey,
                provenance.discoverySource(),
                null,
                provenance.localRouting(),
                provenance.externalMerchantReference(),
                provenance.externalMerchantDomain(),
                provenance.externalProductReference(),
                provenance.externalVariantReference(),
                offer.selectedOptions(),
                offer.identity().components(),
                offer.identity().sellingPlanIdentity()
        );
    }

    private boolean durableAnchor(CatalogProductReference reference) {
        return reference.externalMerchantReference() != null || reference.localRouting() != null;
    }

    private DurableReferenceSnapshot snapshot(CatalogProductReference reference, String policyKey) {
        return new DurableReferenceSnapshot(
                reference.discoverySource().provider().value(),
                reference.discoverySource().type().name(),
                reference.discoverySource().value(),
                reference.localMerchantId(),
                reference.localRouting() == null ? null : reference.localRouting().merchantIntegrationId(),
                reference.externalMerchantReference() == null
                        ? null : reference.externalMerchantReference().value(),
                reference.externalMerchantDomain(),
                reference.externalProductReference().value(),
                reference.externalVariantReference() == null
                        ? null : reference.externalVariantReference().value(),
                json(reference.selectedOptions()),
                json(reference.components()),
                reference.sellingPlanIdentity() == null ? null : json(reference.sellingPlanIdentity()),
                policyKey
        );
    }

    private DurableObservation observation(UserCanonicalProductReference entity) {
        try {
            CatalogProductReference reference = reference(entity);
            CatalogRetentionDecision currentPolicy = dataUsePolicyResolver.resolve(
                    reference.discoverySource(), CatalogPayloadClass.IDENTIFIERS_PROVENANCE);
            if (currentPolicy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY
                    || !currentPolicy.policyKey().equals(entity.getRetentionPolicyKey())) {
                return null;
            }
            OfferIdentity identity = identity(reference);
            if (identity == null || !entity.getOfferKey().equals(identity.key())) {
                return null;
            }
            return new DurableObservation(identity, provenance(reference, entity.getReferenceVerifiedAt()));
        } catch (IllegalArgumentException | JacksonException exception) {
            return null;
        }
    }

    private CatalogProductReference reference(UserCanonicalProductReference entity) throws JacksonException {
        ProviderIdentity provider = new ProviderIdentity(entity.getSourceProvider());
        return new CatalogProductReference(
                entity.getCanonicalProductKey(),
                new DiscoverySourceIdentity(
                        provider,
                        ResultSourceType.valueOf(entity.getSourceType()),
                        entity.getSourceIdentity()
                ),
                entity.getLocalMerchantId(),
                entity.getMerchantIntegrationId() == null
                        ? null : new LocalMerchantRouting(entity.getMerchantIntegrationId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.MERCHANT,
                        provider.value(),
                        entity.getExternalMerchantId()
                ),
                entity.getExternalMerchantDomain(),
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
                options(entity.getSelectedOptionsJson()),
                components(entity.getComponentsJson()),
                sellingPlan(entity.getSellingPlanJson())
        );
    }

    private OfferIdentity identity(CatalogProductReference reference) {
        OfferMerchantScope merchantScope;
        if (reference.externalMerchantReference() != null) {
            merchantScope = OfferMerchantScope.external(reference.externalMerchantReference());
        } else if (reference.localRouting() != null) {
            merchantScope = OfferMerchantScope.localIntegrationFallback(
                    reference.localRouting().merchantIntegrationId());
        } else {
            return null;
        }
        ProviderIdentity provider = reference.discoverySource().provider();
        ExternalIdentifier productIdentity = offerIdentityStrategies.stream()
                .filter(strategy -> strategy.supports(provider))
                .findFirst()
                .map(strategy -> strategy.product(
                        provider,
                        reference.externalProductReference(),
                        reference.externalVariantReference()
                ))
                .orElse(reference.externalProductReference());
        return new OfferIdentity(
                provider,
                merchantScope,
                productIdentity,
                reference.externalVariantReference(),
                reference.selectedOptions(),
                reference.components(),
                reference.sellingPlanIdentity()
        );
    }

    private ResultProvenance provenance(CatalogProductReference reference, Instant verifiedAt) {
        return new ResultProvenance(
                reference.discoverySource().provider(),
                reference.discoverySource(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                new ResultFreshness(verifiedAt, null),
                new ResultSourceReference(
                        reference.discoverySource().type(),
                        reference.discoverySource().value(),
                        null
                )
        );
    }

    private Optional<Offer> offer(String expectedOfferKey, List<DurableObservation> observations) {
        if (observations.isEmpty()) {
            return Optional.empty();
        }
        OfferIdentity identity = observations.getFirst().identity();
        List<ResultProvenance> provenance = observations.stream()
                .filter(observation -> identity.equals(observation.identity()))
                .map(DurableObservation::provenance)
                .distinct()
                .toList();
        if (!expectedOfferKey.equals(identity.key()) || provenance.isEmpty()) {
            return Optional.empty();
        }
        String merchantName = provenance.stream()
                .map(ResultProvenance::externalMerchantDomain)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        return Optional.of(new Offer(
                identity,
                merchantName,
                null,
                null,
                null,
                OfferAvailability.unknown(),
                List.of(),
                null,
                OfferRankingEvidence.unknown(),
                provenance
        ));
    }

    private List<ProductAttribute> options(String value) throws JacksonException {
        List<ProductAttribute> options = objectMapper.readValue(
                value == null || value.isBlank() ? "[]" : value,
                OPTIONS_TYPE
        );
        return options == null ? List.of() : options;
    }

    private List<OfferComponentIdentity> components(String value) throws JacksonException {
        List<OfferComponentIdentity> components = objectMapper.readValue(
                value == null || value.isBlank() ? "[]" : value,
                COMPONENTS_TYPE
        );
        return components == null ? List.of() : components;
    }

    private SellingPlanIdentity sellingPlan(String value) throws JacksonException {
        return value == null || value.isBlank()
                ? null : objectMapper.readValue(value, SellingPlanIdentity.class);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new UserException("Could not serialize canonical product identifiers", exception);
        }
    }

    private List<CanonicalProduct> safeProducts(List<CanonicalProduct> products) {
        return products == null ? List.of() : products.stream().filter(java.util.Objects::nonNull).toList();
    }

    private record DurableObservation(OfferIdentity identity, ResultProvenance provenance) {
    }
}
