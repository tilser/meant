package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DeliveryMethod;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.IdentityEvidenceStrength;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
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
import com.meant.api.module.catalog.service.dto.ProductGroupingDecision;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecisionOutcome;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecisionReason;
import com.meant.api.module.catalog.service.dto.ProductIdentityContradictionKind;
import com.meant.api.module.catalog.service.dto.ProductMaterial;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
import com.meant.api.module.catalog.service.support.CatalogBuyerPresentation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Version 1 grouped product-search response with canonical products and merchant offers")
public record UserGroupedProductSearchV1Response(
        @Schema(description = "Original user search query", requiredMode = Schema.RequiredMode.REQUIRED)
        String query,
        @Schema(description = "Normalized cache identity for the search", requiredMode = Schema.RequiredMode.REQUIRED)
        String normalizedQuery,
        @Schema(description = "Taste and settings profile hash used for the search", requiredMode = Schema.RequiredMode.REQUIRED)
        String profileHash,
        @Schema(description = "Whether results came from a source-approved cache", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cached,
        @Schema(description = "Canonical-product offset applied after grouping", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Canonical-product page size applied after grouping", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(description = "Next canonical-product offset, when another page exists", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer nextOffset,
        @Schema(description = "Whether another canonical-product page exists in the deterministic result window", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore,
        @Schema(
                description = "Whether an upstream source reported more candidates than this live search request could materialize",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean upstreamTruncated,
        @Schema(description = "Typed completion, degradation, and truncation state for every invoked source", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserCatalogSourceStateResponse> sourceStates,
        @Schema(description = "Deterministically ordered canonical products", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CanonicalProductResponse> products,
        @Schema(description = "Total typed reconciliation decisions in the fetched candidate window", requiredMode = Schema.RequiredMode.REQUIRED)
        int groupingDecisionCount,
        @Schema(description = "Whether grouping decisions were omitted by page filtering or the public diagnostic bound", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean groupingDecisionsTruncated,
        @Schema(description = "Typed exact-match and conservative non-match decisions for this page", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductGroupingDecisionResponse> groupingDecisions
) {

    public static UserGroupedProductSearchV1Response from(UserGroupedProductSearchResult result) {
        return new UserGroupedProductSearchV1Response(
                result.query(),
                result.normalizedQuery(),
                result.profileHash(),
                result.cached(),
                result.offset(),
                result.limit(),
                result.nextOffset(),
                result.hasMore(),
                result.upstreamTruncated(),
                result.sourceStates().stream().map(UserCatalogSourceStateResponse::from).toList(),
                result.products().stream()
                        .map(product -> CanonicalProductResponse.from(
                                product,
                                result.productRankingExplanations().get(product.key()),
                                result.productPersonalizations().get(product.key()),
                                result.offerRankingExplanations(),
                                product.offers().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                                        Offer::key, UserOfferCommercialState::discovery))
                        ))
                        .toList(),
                result.groupingDecisionCount(),
                result.groupingDecisionsTruncated(),
                result.groupingDecisions().stream().map(ProductGroupingDecisionResponse::from).toList()
        );
    }

    @Schema(description = "Typed, redacted explanation of a measurable product grouping comparison")
    public record ProductGroupingDecisionResponse(
            @Schema(description = "Stable first offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String leftOfferKey,
            @Schema(description = "Stable second offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String rightOfferKey,
            @Schema(description = "Whether the observations grouped or remained separate", requiredMode = Schema.RequiredMode.REQUIRED)
            ProductGroupingDecisionOutcome outcome,
            @Schema(description = "Stable grouping explanation code", requiredMode = Schema.RequiredMode.REQUIRED)
            ProductGroupingDecisionReason reason,
            @Schema(description = "Decision confidence in basis points", requiredMode = Schema.RequiredMode.REQUIRED)
            int confidenceBasisPoints,
            @Schema(description = "Identity evidence used by the decision", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductIdentityEvidenceResponse> evidence,
            @Schema(description = "Typed contradictory facts that vetoed or qualified the match", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductIdentityContradictionKind> contradictions
    ) {

        static ProductGroupingDecisionResponse from(ProductGroupingDecision decision) {
            return new ProductGroupingDecisionResponse(
                    decision.leftOfferKey(),
                    decision.rightOfferKey(),
                    decision.outcome(),
                    decision.reason(),
                    decision.confidenceBasisPoints(),
                    decision.evidence().stream().map(ProductIdentityEvidenceResponse::from).toList(),
                    decision.contradictions()
            );
        }
    }

    @Schema(description = "Shared product facts plus all exact distinct offers and source observations")
    public record CanonicalProductResponse(
            @Schema(description = "Stable evidence-derived canonical product key", requiredMode = Schema.RequiredMode.REQUIRED)
            String key,
            @Schema(description = "Shared product title", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String title,
            @Schema(description = "Shared product description", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String description,
            @Schema(description = "Shared product media", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductMediaResponse> media,
            @Schema(description = "Shared typed product attributes", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductAttributeResponse> attributes,
            @Schema(description = "Shared typed material facts", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductMaterialResponse> materials,
            @Schema(description = "Shared typed certification facts", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductCertificationResponse> certifications,
            @Schema(description = "Attribution for shared product facts", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductAttributionResponse> attribution,
            @Schema(description = "Identity evidence retained for reconciliation", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductIdentityEvidenceResponse> identityEvidence,
            @Schema(description = "All product-level source observations", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ResultProvenanceResponse> provenance,
            @Schema(description = "Typed, redacted explanation of canonical-product relevance", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ProductRankingExplanationResponse rankingExplanation,
            @Schema(description = "Evidence-backed explanation of this product for the current user", requiredMode = Schema.RequiredMode.REQUIRED)
            CanonicalProductPersonalizationResponse personalization,
            @Schema(description = "Default independently ranked offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String recommendedOfferKey,
            @Schema(description = "Distinct merchant, variant, and selling-plan offers", requiredMode = Schema.RequiredMode.REQUIRED)
            List<OfferResponse> offers
    ) {

        public static CanonicalProductResponse from(CanonicalProduct product) {
            return from(product, null, null, java.util.Map.of(), product == null ? java.util.Map.of() : product.offers().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Offer::key, UserOfferCommercialState::discovery)));
        }

        public static CanonicalProductResponse from(
                CanonicalProduct product,
                ProductRankingExplanation explanation,
                Map<String, OfferRankingExplanation> offerExplanations,
                Map<String, UserOfferCommercialState> commercialStates
        ) {
            return from(product, explanation, null, offerExplanations, commercialStates);
        }

        public static CanonicalProductResponse from(
                CanonicalProduct product,
                ProductRankingExplanation explanation,
                UserCanonicalProductPersonalizationResult personalization,
                Map<String, OfferRankingExplanation> offerExplanations,
                Map<String, UserOfferCommercialState> commercialStates
        ) {
            if (product == null) {
                return null;
            }
            List<ResultProvenance> buyerProvenance = java.util.stream.Stream.concat(
                            product.provenance().stream(),
                            product.offers().stream().flatMap(offer -> offer.provenance().stream())
                    )
                    .distinct()
                    .toList();
            return new CanonicalProductResponse(
                    product.key(),
                    CatalogBuyerPresentation.label(product.title(), buyerProvenance),
                    CatalogBuyerPresentation.text(product.description(), buyerProvenance),
                    product.media().stream()
                            .map(media -> ProductMediaResponse.from(media, buyerProvenance))
                            .filter(media -> media.url() != null)
                            .toList(),
                    product.attributes().stream()
                            .map(attribute -> ProductAttributeResponse.from(
                                    attribute,
                                    buyerProvenance
                            ))
                            .toList(),
                    product.materials().stream()
                            .map(material -> ProductMaterialResponse.from(
                                    material,
                                    buyerProvenance
                            ))
                            .toList(),
                    product.certifications().stream()
                            .map(certification -> ProductCertificationResponse.from(
                                    certification,
                                    buyerProvenance
                            ))
                            .toList(),
                    product.attribution().stream()
                            .map(attribution -> ProductAttributionResponse.from(
                                    attribution,
                                    buyerProvenance
                            ))
                            .toList(),
                    product.identityEvidence().stream().map(ProductIdentityEvidenceResponse::from).toList(),
                    product.provenance().stream().map(ResultProvenanceResponse::from).toList(),
                    ProductRankingExplanationResponse.from(explanation),
                    CanonicalProductPersonalizationResponse.from(personalization, buyerProvenance),
                    product.offers().getFirst().key(),
                    product.offers().stream()
                            .map(offer -> OfferResponse.from(
                                    offer,
                                    offerExplanations.get(offer.key()),
                                    commercialStates.getOrDefault(offer.key(), UserOfferCommercialState.discovery(offer))))
                            .toList()
            );
        }
    }

    @Schema(description = "Evidence-backed preference matches and user-facing explanation for one canonical product")
    public record CanonicalProductPersonalizationResponse(
            @Schema(description = "Concise evidence-backed explanation of why this product fits the current user", requiredMode = Schema.RequiredMode.REQUIRED)
            String whyMeantForYou,
            @Schema(description = "Active user filter IDs supported by explicit canonical-product evidence", requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> matchedFilterIds,
            @Schema(description = "Active filter IDs contradicted by explicit canonical-product evidence", requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> missedFilterIds,
            @Schema(description = "Active filter IDs that available canonical-product facts neither support nor contradict", requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> unknownFilterIds,
            @Schema(description = "Active avoid or require filter IDs that must not be claimed satisfied without evidence", requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> hardConstraintFilterIds
    ) {
        static CanonicalProductPersonalizationResponse from(
                UserCanonicalProductPersonalizationResult personalization,
                List<ResultProvenance> provenance
        ) {
            UserCanonicalProductPersonalizationResult resolved = personalization == null
                    ? UserCanonicalProductPersonalizationResult.searchRelevance()
                    : personalization;
            return new CanonicalProductPersonalizationResponse(
                    CatalogBuyerPresentation.text(resolved.whyMeantForYou(), provenance),
                    resolved.matchedFilterIds(),
                    resolved.missedFilterIds(),
                    resolved.unknownFilterIds(),
                    resolved.hardConstraintFilterIds()
            );
        }
    }

    @Schema(description = "One selectable merchant, variant, and selling-plan offer")
    public record OfferResponse(
            @Schema(description = "Stable versioned offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String key,
            @Schema(description = "Provider and commercial offer identity", requiredMode = Schema.RequiredMode.REQUIRED)
            OfferIdentityResponse identity,
            @Schema(description = "Merchant display name", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchantName,
            @Schema(
                    description = "Verified official storefront origin for buyer display",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            String merchantOrigin,
            @Schema(description = "Variant display title", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String variantTitle,
            @Schema(description = "Current offer price in integer minor units", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            MoneyResponse price,
            @Schema(description = "Comparison/list price in integer minor units", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            MoneyResponse listPrice,
            @Schema(description = "Typed availability for this offer", requiredMode = Schema.RequiredMode.REQUIRED)
            OfferAvailabilityResponse availability,
            @Schema(description = "Typed delivery options observed for this offer", requiredMode = Schema.RequiredMode.REQUIRED)
            List<OfferDeliveryResponse> delivery,
            @Schema(description = "Checkout URL when the source explicitly supplies one", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            URI checkoutUrl,
            @Schema(description = "Selected variant and selling-plan options", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductAttributeResponse> selectedOptions,
            @Schema(description = "Available checkout experience inferred from server-controlled routing facts", requiredMode = Schema.RequiredMode.REQUIRED)
            CheckoutExperienceLevel checkoutExperience,
            @Schema(description = "Authority and freshness of price, availability, and delivery", requiredMode = Schema.RequiredMode.REQUIRED)
            UserOfferCommercialStateResponse commercialState,
            @Schema(description = "Typed, redacted explanation of this offer's independent ordering", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            OfferRankingExplanationResponse rankingExplanation,
            @Schema(description = "Every source observation merged into this exact offer", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ResultProvenanceResponse> provenance
    ) {

        static OfferResponse from(Offer offer) {
            return from(offer, null, UserOfferCommercialState.discovery(offer));
        }

        static OfferResponse from(
                Offer offer,
                OfferRankingExplanation explanation,
                UserOfferCommercialState commercialState
        ) {
            URI buyerSafeCheckoutUrl =
                    CatalogBuyerPresentation.safeUri(offer.checkoutUrl(), offer.provenance());
            return new OfferResponse(
                    offer.key(),
                    OfferIdentityResponse.from(offer.identity(), offer.provenance()),
                    CatalogBuyerPresentation.label(offer.merchantName(), offer.provenance()),
                    CatalogBuyerPresentation.merchantOrigin(offer.provenance()),
                    CatalogBuyerPresentation.text(offer.variantTitle(), offer.provenance()),
                    MoneyResponse.from(offer.price()), MoneyResponse.from(offer.listPrice()),
                    OfferAvailabilityResponse.from(offer.availability()),
                    offer.delivery().stream()
                            .map(delivery -> OfferDeliveryResponse.from(
                                    delivery,
                                    offer.provenance()
                            ))
                            .toList(),
                    buyerSafeCheckoutUrl,
                    offer.selectedOptions().stream()
                            .map(attribute -> ProductAttributeResponse.from(
                                    attribute,
                                    offer.provenance()
                            ))
                            .toList(),
                    checkoutExperience(offer, buyerSafeCheckoutUrl),
                    UserOfferCommercialStateResponse.from(commercialState),
                    OfferRankingExplanationResponse.from(explanation),
                    offer.provenance().stream().map(ResultProvenanceResponse::from).toList()
            );
        }

        private static CheckoutExperienceLevel checkoutExperience(
                Offer offer,
                URI buyerSafeCheckoutUrl
        ) {
            if (offer.provenance().stream().anyMatch(value -> value.localRouting() != null)
                    && Boolean.TRUE.equals(offer.rankingEvidence().checkoutCapable())) {
                return CheckoutExperienceLevel.MEANT_MANAGED;
            }
            return buyerSafeCheckoutUrl == null
                    ? CheckoutExperienceLevel.UNKNOWN
                    : CheckoutExperienceLevel.PROVIDER_HANDOFF;
        }
    }

    public enum CheckoutExperienceLevel {
        MEANT_MANAGED,
        PROVIDER_HANDOFF,
        UNKNOWN
    }

    @Schema(description = "Provider-defined identities that determine offer uniqueness")
    public record OfferIdentityResponse(
            @Schema(description = "Commerce provider identity", requiredMode = Schema.RequiredMode.REQUIRED)
            String provider,
            @Deprecated
            @Schema(
                    description = "Deprecated compatibility field populated only for a local-integration identity fallback",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    deprecated = true
            )
            UUID merchantIntegrationId,
            @Schema(
                    description = "Deprecated compatibility view of the external merchant scope",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    deprecated = true
            )
            ExternalIdentifierResponse externalMerchantIdentity,
            @Schema(description = "Authoritative external or local-fallback seller scope", requiredMode = Schema.RequiredMode.REQUIRED)
            OfferMerchantScopeResponse merchantScope,
            @Schema(description = "External product reference", requiredMode = Schema.RequiredMode.REQUIRED)
            ExternalIdentifierResponse externalProductIdentity,
            @Schema(description = "External variant reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse externalVariantIdentity,
            @Schema(description = "Order-independent bundle or composite component identity", requiredMode = Schema.RequiredMode.REQUIRED)
            List<OfferComponentIdentityResponse> components,
            @Schema(description = "Selling-plan identity and context", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            SellingPlanIdentityResponse sellingPlanIdentity
    ) {

        static OfferIdentityResponse from(
                OfferIdentity identity,
                List<ResultProvenance> provenance
        ) {
            return new OfferIdentityResponse(
                    identity.provider().value(),
                    identity.merchantScope().merchantIntegrationFallbackId(),
                    ExternalIdentifierResponse.from(identity.merchantScope().externalMerchantIdentity()),
                    OfferMerchantScopeResponse.from(identity.merchantScope()),
                    ExternalIdentifierResponse.from(identity.externalProductIdentity()),
                    ExternalIdentifierResponse.from(identity.externalVariantIdentity()),
                    identity.components().stream()
                            .map(component -> OfferComponentIdentityResponse.from(
                                    component,
                                    provenance
                            ))
                            .toList(),
                    SellingPlanIdentityResponse.from(identity.sellingPlanIdentity(), provenance)
            );
        }
    }

    @Schema(description = "Stable seller scope independent of discovery source and execution routing")
    public record OfferMerchantScopeResponse(
            @Schema(description = "Identity authority used for this seller scope", requiredMode = Schema.RequiredMode.REQUIRED)
            OfferMerchantScopeType type,
            @Schema(description = "Provider-namespaced stable external merchant identity", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse externalMerchantIdentity,
            @Schema(
                    description = "Local MerchantIntegration identity fallback when the provider exposes no stable merchant identity",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            UUID merchantIntegrationFallbackId
    ) {

        static OfferMerchantScopeResponse from(OfferMerchantScope scope) {
            return new OfferMerchantScopeResponse(
                    scope.type(),
                    ExternalIdentifierResponse.from(scope.externalMerchantIdentity()),
                    scope.merchantIntegrationFallbackId()
            );
        }
    }

    @Schema(description = "Identity-bearing product component in a bundle or composite offer")
    public record OfferComponentIdentityResponse(
            @Schema(description = "External product identity for the component", requiredMode = Schema.RequiredMode.REQUIRED)
            ExternalIdentifierResponse externalProductIdentity,
            @Schema(description = "External variant identity for the component", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse externalVariantIdentity,
            @Schema(description = "Positive component quantity", requiredMode = Schema.RequiredMode.REQUIRED)
            int quantity,
            @Schema(description = "Canonically ordered selected component options", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductAttributeResponse> selectedOptions
    ) {

        static OfferComponentIdentityResponse from(
                OfferComponentIdentity component,
                List<ResultProvenance> provenance
        ) {
            return new OfferComponentIdentityResponse(
                    ExternalIdentifierResponse.from(component.externalProductIdentity()),
                    ExternalIdentifierResponse.from(component.externalVariantIdentity()),
                    component.quantity(),
                    component.selectedOptions().stream()
                            .map(attribute -> ProductAttributeResponse.from(attribute, provenance))
                            .toList()
            );
        }
    }

    @Schema(description = "Typed selling-plan references and identity-bearing option context")
    public record SellingPlanIdentityResponse(
            @Schema(description = "External selling-plan group reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse groupReference,
            @Schema(description = "External selling-plan reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse planReference,
            @Schema(description = "Canonically ordered selling-plan options", requiredMode = Schema.RequiredMode.REQUIRED)
            List<SellingPlanOptionResponse> options
    ) {

        static SellingPlanIdentityResponse from(
                SellingPlanIdentity identity,
                List<ResultProvenance> provenance
        ) {
            return identity == null ? null : new SellingPlanIdentityResponse(
                    ExternalIdentifierResponse.from(identity.groupReference()),
                    ExternalIdentifierResponse.from(identity.planReference()),
                    identity.options().stream()
                            .map(option -> SellingPlanOptionResponse.from(option, provenance))
                            .toList()
            );
        }
    }

    @Schema(description = "One selling-plan option that participates in offer identity")
    public record SellingPlanOptionResponse(
            @Schema(description = "Option name", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Provider-defined option value", requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static SellingPlanOptionResponse from(
                SellingPlanOption option,
                List<ResultProvenance> provenance
        ) {
            return new SellingPlanOptionResponse(
                    CatalogBuyerPresentation.text(option.name(), provenance),
                    CatalogBuyerPresentation.text(option.value(), provenance)
            );
        }
    }

    @Schema(description = "Typed external reference; provider-defined values remain case-sensitive")
    public record ExternalIdentifierResponse(
            @Schema(description = "Role or standard represented by the identifier", requiredMode = Schema.RequiredMode.REQUIRED)
            ExternalIdentifierType type,
            @Schema(description = "Provider or standard namespace", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String namespace,
            @Schema(description = "Case-sensitive external value", requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ExternalIdentifierResponse from(ExternalIdentifier identifier) {
            return identifier == null ? null : new ExternalIdentifierResponse(
                    identifier.type(), identifier.namespace(), identifier.value());
        }
    }

    @Schema(description = "Integer-minor-unit monetary value")
    public record MoneyResponse(
            @Schema(description = "Signed integer amount in currency minor units", requiredMode = Schema.RequiredMode.REQUIRED)
            long minorUnits,
            @Schema(description = "Uppercase ISO-style currency code", requiredMode = Schema.RequiredMode.REQUIRED)
            String currency
    ) {

        static MoneyResponse from(Money money) {
            return money == null ? null : new MoneyResponse(money.minorUnits(), money.currency());
        }
    }

    @Schema(description = "Typed offer availability")
    public record OfferAvailabilityResponse(
            @Schema(description = "Normalized availability state", requiredMode = Schema.RequiredMode.REQUIRED)
            OfferAvailabilityStatus status,
            @Schema(description = "Observed available quantity", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer quantity,
            @Schema(description = "Future availability time", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant availableAt
    ) {

        static OfferAvailabilityResponse from(OfferAvailability availability) {
            return new OfferAvailabilityResponse(
                    availability.status(), availability.quantity(), availability.availableAt());
        }
    }

    @Schema(description = "Typed delivery method, estimate, destination, and cost")
    public record OfferDeliveryResponse(
            @Schema(description = "Fulfillment method", requiredMode = Schema.RequiredMode.REQUIRED)
            DeliveryMethod method,
            @Schema(description = "Destination region used for this estimate", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String destinationRegion,
            @Schema(description = "Minimum estimated business days", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer minimumBusinessDays,
            @Schema(description = "Maximum estimated business days", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer maximumBusinessDays,
            @Schema(description = "Delivery cost", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            MoneyResponse cost
    ) {

        static OfferDeliveryResponse from(
                OfferDelivery delivery,
                List<ResultProvenance> provenance
        ) {
            return new OfferDeliveryResponse(
                    delivery.method(),
                    CatalogBuyerPresentation.text(delivery.destinationRegion(), provenance),
                    delivery.minimumBusinessDays(),
                    delivery.maximumBusinessDays(),
                    MoneyResponse.from(delivery.cost())
            );
        }
    }

    @Schema(description = "Typed product media")
    public record ProductMediaResponse(
            @Schema(description = "Media kind", requiredMode = Schema.RequiredMode.REQUIRED)
            ProductMediaType type,
            @Schema(description = "Media URL", requiredMode = Schema.RequiredMode.REQUIRED)
            URI url,
            @Schema(description = "Alternative text", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String altText,
            @Schema(description = "Pixel width", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer width,
            @Schema(description = "Pixel height", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer height
    ) {

        static ProductMediaResponse from(
                ProductMedia media,
                List<ResultProvenance> provenance
        ) {
            return new ProductMediaResponse(
                    media.type(),
                    CatalogBuyerPresentation.safeUri(media.url(), provenance),
                    CatalogBuyerPresentation.text(media.altText(), provenance),
                    media.width(),
                    media.height()
            );
        }
    }

    @Schema(name = "CanonicalProductAttributeResponse", description = "Typed product or selected-option attribute")
    public record ProductAttributeResponse(
            @Schema(description = "Optional attribute group", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String group,
            @Schema(description = "Attribute name", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Attribute value", requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductAttributeResponse from(
                ProductAttribute attribute,
                List<ResultProvenance> provenance
        ) {
            return new ProductAttributeResponse(
                    CatalogBuyerPresentation.label(attribute.group(), provenance),
                    CatalogBuyerPresentation.label(attribute.name(), provenance),
                    CatalogBuyerPresentation.label(attribute.value(), provenance)
            );
        }
    }

    @Schema(description = "Typed product material")
    public record ProductMaterialResponse(
            @Schema(description = "Material name", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Composition percentage in basis points", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer percentageBasisPoints
    ) {

        static ProductMaterialResponse from(
                ProductMaterial material,
                List<ResultProvenance> provenance
        ) {
            return new ProductMaterialResponse(
                    CatalogBuyerPresentation.label(material.name(), provenance),
                    material.percentageBasisPoints()
            );
        }
    }

    @Schema(description = "Typed product certification")
    public record ProductCertificationResponse(
            @Schema(description = "Certification name", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Issuing organization", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String issuer,
            @Schema(description = "Certificate identifier", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String identifier,
            @Schema(description = "Certificate verification URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            URI verificationUrl
    ) {

        static ProductCertificationResponse from(
                ProductCertification certification,
                List<ResultProvenance> provenance
        ) {
            return new ProductCertificationResponse(
                    CatalogBuyerPresentation.label(certification.name(), provenance),
                    CatalogBuyerPresentation.label(certification.issuer(), provenance),
                    CatalogBuyerPresentation.text(certification.identifier(), provenance),
                    CatalogBuyerPresentation.safeUri(
                            certification.verificationUrl(),
                            provenance
                    )
            );
        }
    }

    @Schema(description = "Attribution for shared product facts")
    public record ProductAttributionResponse(
            @Schema(description = "Human-readable attribution label", requiredMode = Schema.RequiredMode.REQUIRED)
            String label,
            @Schema(description = "Attribution URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            URI url,
            @Schema(description = "Source behind the attribution", requiredMode = Schema.RequiredMode.REQUIRED)
            ResultSourceReferenceResponse sourceReference
    ) {

        static ProductAttributionResponse from(
                ProductAttribution attribution,
                List<ResultProvenance> provenance
        ) {
            return new ProductAttributionResponse(
                    CatalogBuyerPresentation.text(attribution.label(), provenance),
                    CatalogBuyerPresentation.safeUri(attribution.url(), provenance),
                    ResultSourceReferenceResponse.from(attribution.sourceReference())
            );
        }
    }

    @Schema(description = "Explicit-confidence product identity evidence")
    public record ProductIdentityEvidenceResponse(
            @Schema(description = "Evidence kind", requiredMode = Schema.RequiredMode.REQUIRED)
            ProductIdentityEvidenceKind kind,
            @Schema(description = "Trust level controlling exact grouping eligibility", requiredMode = Schema.RequiredMode.REQUIRED)
            IdentityEvidenceStrength strength,
            @Schema(description = "Confidence in basis points from 0 to 10000", requiredMode = Schema.RequiredMode.REQUIRED)
            int confidenceBasisPoints,
            @Schema(description = "Typed identifiers carried by the evidence", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ExternalIdentifierResponse> identifiers,
            @Schema(description = "Source that asserted the evidence", requiredMode = Schema.RequiredMode.REQUIRED)
            ResultSourceReferenceResponse sourceReference
    ) {

        static ProductIdentityEvidenceResponse from(ProductIdentityEvidence evidence) {
            return new ProductIdentityEvidenceResponse(
                    evidence.kind(),
                    evidence.strength(),
                    evidence.confidenceBasisPoints(),
                    evidence.identifiers().stream().map(ExternalIdentifierResponse::from).toList(),
                    ResultSourceReferenceResponse.from(evidence.sourceReference())
            );
        }
    }

    @Schema(description = "Provider evidence, discovery identity, optional local routing, freshness, and debugging source")
    public record ResultProvenanceResponse(
            @Schema(description = "Commerce provider identity", requiredMode = Schema.RequiredMode.REQUIRED)
            String provider,
            @Deprecated
            @Schema(
                    description = "Deprecated compatibility view of localRouting.merchantIntegrationId",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                    deprecated = true
            )
            UUID merchantIntegrationId,
            @Schema(description = "Stable identity of the catalog, storefront, cache, or other observing path", requiredMode = Schema.RequiredMode.REQUIRED)
            DiscoverySourceIdentityResponse discoverySource,
            @Schema(description = "Optional resolved Meant MerchantIntegration execution link", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            LocalMerchantRoutingResponse localRouting,
            @Schema(description = "External merchant reference when supplied by the provider", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse externalMerchantReference,
            @Schema(description = "External product reference", requiredMode = Schema.RequiredMode.REQUIRED)
            ExternalIdentifierResponse externalProductReference,
            @Schema(description = "External variant reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse externalVariantReference,
            @Schema(description = "Observation timestamp and freshness", requiredMode = Schema.RequiredMode.REQUIRED)
            ResultFreshnessResponse freshness,
            @Schema(description = "Typed source reference for debugging", requiredMode = Schema.RequiredMode.REQUIRED)
            ResultSourceReferenceResponse sourceReference
    ) {

        static ResultProvenanceResponse from(ResultProvenance provenance) {
            return new ResultProvenanceResponse(
                    provenance.provider().value(),
                    provenance.localRouting() == null ? null : provenance.localRouting().merchantIntegrationId(),
                    DiscoverySourceIdentityResponse.from(provenance.discoverySource()),
                    LocalMerchantRoutingResponse.from(provenance.localRouting()),
                    ExternalIdentifierResponse.from(provenance.externalMerchantReference()),
                    ExternalIdentifierResponse.from(provenance.externalProductReference()),
                    ExternalIdentifierResponse.from(provenance.externalVariantReference()),
                    ResultFreshnessResponse.from(provenance.freshness()),
                    ResultSourceReferenceResponse.from(provenance.sourceReference())
            );
        }
    }

    @Schema(description = "Stable typed identity of one discovery path")
    public record DiscoverySourceIdentityResponse(
            @Schema(description = "Commerce provider that owns the discovery source", requiredMode = Schema.RequiredMode.REQUIRED)
            String provider,
            @Schema(description = "Discovery source category", requiredMode = Schema.RequiredMode.REQUIRED)
            ResultSourceType type,
            @Schema(description = "Stable provider-local source identifier", requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        public static DiscoverySourceIdentityResponse from(DiscoverySourceIdentity source) {
            return source == null
                    ? null
                    : new DiscoverySourceIdentityResponse(source.provider().value(), source.type(), source.value());
        }
    }

    @Schema(description = "Resolved Meant merchant routing link used for later execution")
    public record LocalMerchantRoutingResponse(
            @Schema(description = "Merchant-owned MerchantIntegration primary key", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID merchantIntegrationId
    ) {

        static LocalMerchantRoutingResponse from(LocalMerchantRouting routing) {
            return routing == null ? null : new LocalMerchantRoutingResponse(routing.merchantIntegrationId());
        }
    }

    @Schema(description = "Observation timestamp and optional source-provided freshness deadline")
    public record ResultFreshnessResponse(
            @Schema(description = "Time the source was observed", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant observedAt,
            @Schema(description = "Time after which the observation is stale", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant freshUntil
    ) {

        public static ResultFreshnessResponse from(ResultFreshness freshness) {
            return freshness == null ? null : new ResultFreshnessResponse(freshness.observedAt(), freshness.freshUntil());
        }
    }

    @Schema(description = "Typed source path sufficient to debug or reconcile an observation")
    public record ResultSourceReferenceResponse(
            @Schema(description = "Discovery source category", requiredMode = Schema.RequiredMode.REQUIRED)
            ResultSourceType type,
            @Schema(description = "Source-local debugging reference", requiredMode = Schema.RequiredMode.REQUIRED)
            String reference
    ) {

        static ResultSourceReferenceResponse from(ResultSourceReference sourceReference) {
            return new ResultSourceReferenceResponse(
                    sourceReference.type(), sourceReference.reference());
        }
    }
}
