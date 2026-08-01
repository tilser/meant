package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.IdentityEvidenceStrength;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.OfferDelivery;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.OfferMerchantScopeType;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductAttribution;
import com.meant.api.module.catalog.service.dto.ProductCertification;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidence;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind;
import com.meant.api.module.catalog.service.dto.ProductMaterial;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
import com.meant.api.module.catalog.service.support.CatalogBuyerPresentation;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stable product-artifact payload shaped like the existing grouped-product API contract.
 *
 * <p>Catalog service records deliberately omit derived wire fields such as offer keys and selected
 * options. Agent artifacts cross the same frontend boundary as grouped search responses, so they
 * must project those fields explicitly instead of serializing internal domain records directly.
 */
public record AgentCanonicalProductArtifact(
        String key,
        String title,
        String description,
        List<ProductMedia> media,
        List<ProductAttribute> attributes,
        List<ProductMaterial> materials,
        List<ProductCertification> certifications,
        List<AgentProductAttributionArtifact> attribution,
        List<AgentProductIdentityEvidenceArtifact> identityEvidence,
        List<AgentResultProvenanceArtifact> provenance,
        ProductRankingExplanation rankingExplanation,
        UserCanonicalProductPersonalizationResult personalization,
        String recommendedOfferKey,
        List<AgentCanonicalOfferArtifact> offers
) {

    public static AgentCanonicalProductArtifact discovery(
            CanonicalProduct product,
            ProductRankingExplanation rankingExplanation,
            UserCanonicalProductPersonalizationResult personalization,
            Map<String, OfferRankingExplanation> offerRankingExplanations
    ) {
        Map<String, UserOfferCommercialState> commercialStates = product.offers().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Offer::key,
                        UserOfferCommercialState::discovery
                ));
        return from(
                product,
                product.offers().getFirst().key(),
                rankingExplanation,
                personalization,
                offerRankingExplanations,
                commercialStates
        );
    }

    public static AgentCanonicalProductArtifact detail(UserProductDetailResult detail) {
        return from(
                detail.product(),
                detail.recommendedOfferKey(),
                detail.productRankingExplanation(),
                detail.personalization(),
                detail.offerRankingExplanations(),
                detail.commercialStates()
        );
    }

    private static AgentCanonicalProductArtifact from(
            CanonicalProduct product,
            String recommendedOfferKey,
            ProductRankingExplanation rankingExplanation,
            UserCanonicalProductPersonalizationResult personalization,
            Map<String, OfferRankingExplanation> offerRankingExplanations,
            Map<String, UserOfferCommercialState> commercialStates
    ) {
        Map<String, OfferRankingExplanation> rankings = offerRankingExplanations == null
                ? Map.of()
                : offerRankingExplanations;
        Map<String, UserOfferCommercialState> states = commercialStates == null
                ? Map.of()
                : commercialStates;
        List<ResultProvenance> buyerProvenance = Stream.concat(
                        product.provenance().stream(),
                        product.offers().stream().flatMap(offer -> offer.provenance().stream())
                )
                .distinct()
                .toList();
        UserCanonicalProductPersonalizationResult buyerPersonalization =
                buyerPersonalization(personalization, buyerProvenance);
        return new AgentCanonicalProductArtifact(
                product.key(),
                CatalogBuyerPresentation.label(product.title(), buyerProvenance),
                CatalogBuyerPresentation.text(product.description(), buyerProvenance),
                product.media().stream()
                        .flatMap(media -> Stream.ofNullable(
                                CatalogBuyerPresentation.safeUri(media.url(), buyerProvenance)
                        ).map(url -> new ProductMedia(
                                        media.type(),
                                        url,
                                        CatalogBuyerPresentation.text(
                                                media.altText(),
                                                buyerProvenance
                                        ),
                                        media.width(),
                                        media.height()
                                )))
                        .toList(),
                product.attributes().stream()
                        .map(attribute -> sanitizedAttribute(attribute, buyerProvenance))
                        .toList(),
                product.materials().stream()
                        .map(material -> new ProductMaterial(
                                CatalogBuyerPresentation.label(
                                        material.name(),
                                        buyerProvenance
                                ),
                                material.percentageBasisPoints()
                        ))
                        .toList(),
                product.certifications().stream()
                        .map(certification -> new ProductCertification(
                                CatalogBuyerPresentation.label(
                                        certification.name(),
                                        buyerProvenance
                                ),
                                CatalogBuyerPresentation.label(
                                        certification.issuer(),
                                        buyerProvenance
                                ),
                                CatalogBuyerPresentation.text(
                                        certification.identifier(),
                                        buyerProvenance
                                ),
                                CatalogBuyerPresentation.safeUri(
                                        certification.verificationUrl(),
                                        buyerProvenance
                                )
                        ))
                        .toList(),
                product.attribution().stream()
                        .map(attribution -> AgentProductAttributionArtifact.from(
                                attribution,
                                buyerProvenance
                        ))
                        .toList(),
                product.identityEvidence().stream()
                        .map(AgentProductIdentityEvidenceArtifact::from)
                        .toList(),
                product.provenance().stream().map(AgentResultProvenanceArtifact::from).toList(),
                rankingExplanation,
                buyerPersonalization,
                recommendedOfferKey == null || recommendedOfferKey.isBlank()
                        ? product.offers().getFirst().key()
                        : recommendedOfferKey,
                product.offers().stream()
                        .map(offer -> AgentCanonicalOfferArtifact.from(
                                offer,
                                rankings.get(offer.key()),
                                states.getOrDefault(offer.key(), UserOfferCommercialState.discovery(offer))
                        ))
                        .toList()
        );
    }

    private static ProductAttribute sanitizedAttribute(
            ProductAttribute attribute,
            List<ResultProvenance> provenance
    ) {
        return new ProductAttribute(
                CatalogBuyerPresentation.label(attribute.group(), provenance),
                CatalogBuyerPresentation.label(attribute.name(), provenance),
                CatalogBuyerPresentation.label(attribute.value(), provenance)
        );
    }

    private static UserCanonicalProductPersonalizationResult buyerPersonalization(
            UserCanonicalProductPersonalizationResult personalization,
            List<ResultProvenance> provenance
    ) {
        UserCanonicalProductPersonalizationResult resolved = personalization == null
                ? UserCanonicalProductPersonalizationResult.searchRelevance()
                : personalization;
        return new UserCanonicalProductPersonalizationResult(
                CatalogBuyerPresentation.text(resolved.whyMeantForYou(), provenance),
                resolved.matchedFilterIds(),
                resolved.missedFilterIds(),
                resolved.unknownFilterIds(),
                resolved.hardConstraintFilterIds()
        );
    }

    public record AgentCanonicalOfferArtifact(
            String key,
            AgentOfferIdentityArtifact identity,
            String merchantName,
            String merchantOrigin,
            String variantTitle,
            Money price,
            Money listPrice,
            OfferAvailability availability,
            List<OfferDelivery> delivery,
            URI checkoutUrl,
            List<ProductAttribute> selectedOptions,
            AgentCheckoutExperience checkoutExperience,
            UserOfferCommercialState commercialState,
            OfferRankingExplanation rankingExplanation,
            List<AgentResultProvenanceArtifact> provenance
    ) {

        private static AgentCanonicalOfferArtifact from(
                Offer offer,
                OfferRankingExplanation rankingExplanation,
                UserOfferCommercialState commercialState
        ) {
            URI buyerSafeCheckoutUrl =
                    CatalogBuyerPresentation.safeUri(offer.checkoutUrl(), offer.provenance());
            return new AgentCanonicalOfferArtifact(
                    offer.key(),
                    AgentOfferIdentityArtifact.from(offer.identity(), offer.provenance()),
                    CatalogBuyerPresentation.label(offer.merchantName(), offer.provenance()),
                    CatalogBuyerPresentation.merchantOrigin(offer.provenance()),
                    CatalogBuyerPresentation.text(offer.variantTitle(), offer.provenance()),
                    offer.price(),
                    offer.listPrice(),
                    offer.availability(),
                    offer.delivery().stream()
                            .map(delivery -> new OfferDelivery(
                                    delivery.method(),
                                    CatalogBuyerPresentation.text(
                                            delivery.destinationRegion(),
                                            offer.provenance()
                                    ),
                                    delivery.minimumBusinessDays(),
                                    delivery.maximumBusinessDays(),
                                    delivery.cost()
                            ))
                            .toList(),
                    buyerSafeCheckoutUrl,
                    offer.selectedOptions().stream()
                            .map(attribute -> sanitizedAttribute(
                                    attribute,
                                    offer.provenance()
                            ))
                            .toList(),
                    checkoutExperience(offer, buyerSafeCheckoutUrl),
                    commercialState,
                    rankingExplanation,
                    offer.provenance().stream().map(AgentResultProvenanceArtifact::from).toList()
            );
        }

        private static AgentCheckoutExperience checkoutExperience(
                Offer offer,
                URI buyerSafeCheckoutUrl
        ) {
            if (offer.provenance().stream().anyMatch(value -> value.localRouting() != null)
                    && Boolean.TRUE.equals(offer.rankingEvidence().checkoutCapable())) {
                return AgentCheckoutExperience.MEANT_MANAGED;
            }
            return buyerSafeCheckoutUrl == null
                    ? AgentCheckoutExperience.UNKNOWN
                    : AgentCheckoutExperience.PROVIDER_HANDOFF;
        }
    }

    public record AgentOfferIdentityArtifact(
            String provider,
            UUID merchantIntegrationId,
            ExternalIdentifier externalMerchantIdentity,
            AgentOfferMerchantScopeArtifact merchantScope,
            ExternalIdentifier externalProductIdentity,
            ExternalIdentifier externalVariantIdentity,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlanIdentity
    ) {

        private static AgentOfferIdentityArtifact from(
                OfferIdentity identity,
                List<ResultProvenance> provenance
        ) {
            OfferMerchantScope merchantScope = identity.merchantScope();
            return new AgentOfferIdentityArtifact(
                    identity.provider().value(),
                    merchantScope.merchantIntegrationFallbackId(),
                    merchantScope.externalMerchantIdentity(),
                    AgentOfferMerchantScopeArtifact.from(merchantScope),
                    identity.externalProductIdentity(),
                    identity.externalVariantIdentity(),
                    identity.components().stream()
                            .map(component -> sanitizedComponent(component, provenance))
                            .toList(),
                    sanitizedSellingPlan(identity.sellingPlanIdentity(), provenance)
            );
        }

        private static OfferComponentIdentity sanitizedComponent(
                OfferComponentIdentity component,
                List<ResultProvenance> provenance
        ) {
            return new OfferComponentIdentity(
                    component.externalProductIdentity(),
                    component.externalVariantIdentity(),
                    component.quantity(),
                    component.selectedOptions().stream()
                            .map(attribute -> sanitizedAttribute(attribute, provenance))
                            .toList()
            );
        }

        private static SellingPlanIdentity sanitizedSellingPlan(
                SellingPlanIdentity sellingPlan,
                List<ResultProvenance> provenance
        ) {
            return sellingPlan == null
                    ? null
                    : new SellingPlanIdentity(
                            sellingPlan.groupReference(),
                            sellingPlan.planReference(),
                            sellingPlan.options().stream()
                                    .map(option -> new SellingPlanOption(
                                            CatalogBuyerPresentation.text(
                                                    option.name(),
                                                    provenance
                                            ),
                                            CatalogBuyerPresentation.text(
                                                    option.value(),
                                                    provenance
                                            )
                                    ))
                                    .toList()
                    );
        }
    }

    public record AgentOfferMerchantScopeArtifact(
            OfferMerchantScopeType type,
            ExternalIdentifier externalMerchantIdentity,
            UUID merchantIntegrationFallbackId
    ) {

        private static AgentOfferMerchantScopeArtifact from(OfferMerchantScope scope) {
            return new AgentOfferMerchantScopeArtifact(
                    scope.type(),
                    scope.externalMerchantIdentity(),
                    scope.merchantIntegrationFallbackId()
            );
        }
    }

    public record AgentResultProvenanceArtifact(
            String provider,
            UUID merchantIntegrationId,
            AgentDiscoverySourceIdentityArtifact discoverySource,
            LocalMerchantRouting localRouting,
            ExternalIdentifier externalMerchantReference,
            ExternalIdentifier externalProductReference,
            ExternalIdentifier externalVariantReference,
            ResultFreshness freshness,
            AgentResultSourceReferenceArtifact sourceReference
    ) {

        private static AgentResultProvenanceArtifact from(ResultProvenance provenance) {
            return new AgentResultProvenanceArtifact(
                    provenance.provider().value(),
                    provenance.localRouting() == null
                            ? null
                            : provenance.localRouting().merchantIntegrationId(),
                    AgentDiscoverySourceIdentityArtifact.from(provenance.discoverySource()),
                    provenance.localRouting(),
                    provenance.externalMerchantReference(),
                    provenance.externalProductReference(),
                    provenance.externalVariantReference(),
                    provenance.freshness(),
                    AgentResultSourceReferenceArtifact.from(provenance.sourceReference())
            );
        }
    }

    public record AgentProductAttributionArtifact(
            String label,
            URI url,
            AgentResultSourceReferenceArtifact sourceReference
    ) {

        private static AgentProductAttributionArtifact from(
                ProductAttribution attribution,
                List<ResultProvenance> provenance
        ) {
            return new AgentProductAttributionArtifact(
                    CatalogBuyerPresentation.text(attribution.label(), provenance),
                    CatalogBuyerPresentation.safeUri(attribution.url(), provenance),
                    AgentResultSourceReferenceArtifact.from(attribution.sourceReference())
            );
        }
    }

    public record AgentProductIdentityEvidenceArtifact(
            ProductIdentityEvidenceKind kind,
            IdentityEvidenceStrength strength,
            int confidenceBasisPoints,
            List<ExternalIdentifier> identifiers,
            AgentResultSourceReferenceArtifact sourceReference
    ) {

        private static AgentProductIdentityEvidenceArtifact from(ProductIdentityEvidence evidence) {
            return new AgentProductIdentityEvidenceArtifact(
                    evidence.kind(),
                    evidence.strength(),
                    evidence.confidenceBasisPoints(),
                    evidence.identifiers(),
                    AgentResultSourceReferenceArtifact.from(evidence.sourceReference())
            );
        }
    }

    public record AgentResultSourceReferenceArtifact(
            ResultSourceType type,
            String reference
    ) {

        private static AgentResultSourceReferenceArtifact from(ResultSourceReference sourceReference) {
            return new AgentResultSourceReferenceArtifact(
                    sourceReference.type(),
                    sourceReference.reference()
            );
        }
    }

    public record AgentDiscoverySourceIdentityArtifact(
            String provider,
            ResultSourceType type,
            String value
    ) {

        private static AgentDiscoverySourceIdentityArtifact from(DiscoverySourceIdentity source) {
            return new AgentDiscoverySourceIdentityArtifact(
                    source.provider().value(),
                    source.type(),
                    source.value()
            );
        }
    }

    public enum AgentCheckoutExperience {
        MEANT_MANAGED,
        PROVIDER_HANDOFF,
        UNKNOWN
    }
}
