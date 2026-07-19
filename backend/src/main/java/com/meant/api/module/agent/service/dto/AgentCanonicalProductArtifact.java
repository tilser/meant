package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
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
import com.meant.api.module.catalog.service.dto.ProductMaterial;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        List<ProductAttribution> attribution,
        List<ProductIdentityEvidence> identityEvidence,
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
        return new AgentCanonicalProductArtifact(
                product.key(),
                product.title(),
                product.description(),
                product.media(),
                product.attributes(),
                product.materials(),
                product.certifications(),
                product.attribution(),
                product.identityEvidence(),
                product.provenance().stream().map(AgentResultProvenanceArtifact::from).toList(),
                rankingExplanation,
                personalization == null
                        ? UserCanonicalProductPersonalizationResult.searchRelevance()
                        : personalization,
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

    public record AgentCanonicalOfferArtifact(
            String key,
            AgentOfferIdentityArtifact identity,
            String merchantName,
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
            return new AgentCanonicalOfferArtifact(
                    offer.key(),
                    AgentOfferIdentityArtifact.from(offer.identity()),
                    offer.merchantName(),
                    offer.variantTitle(),
                    offer.price(),
                    offer.listPrice(),
                    offer.availability(),
                    offer.delivery(),
                    offer.checkoutUrl(),
                    offer.selectedOptions(),
                    checkoutExperience(offer),
                    commercialState,
                    rankingExplanation,
                    offer.provenance().stream().map(AgentResultProvenanceArtifact::from).toList()
            );
        }

        private static AgentCheckoutExperience checkoutExperience(Offer offer) {
            if (offer.provenance().stream().anyMatch(value -> value.localRouting() != null)
                    && Boolean.TRUE.equals(offer.rankingEvidence().checkoutCapable())) {
                return AgentCheckoutExperience.MEANT_MANAGED;
            }
            return offer.checkoutUrl() == null
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

        private static AgentOfferIdentityArtifact from(OfferIdentity identity) {
            OfferMerchantScope merchantScope = identity.merchantScope();
            return new AgentOfferIdentityArtifact(
                    identity.provider().value(),
                    merchantScope.merchantIntegrationFallbackId(),
                    merchantScope.externalMerchantIdentity(),
                    AgentOfferMerchantScopeArtifact.from(merchantScope),
                    identity.externalProductIdentity(),
                    identity.externalVariantIdentity(),
                    identity.components(),
                    identity.sellingPlanIdentity()
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
            String externalMerchantDomain,
            ExternalIdentifier externalProductReference,
            ExternalIdentifier externalVariantReference,
            ResultFreshness freshness,
            ResultSourceReference sourceReference
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
                    provenance.externalMerchantDomain(),
                    provenance.externalProductReference(),
                    provenance.externalVariantReference(),
                    provenance.freshness(),
                    provenance.sourceReference()
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
