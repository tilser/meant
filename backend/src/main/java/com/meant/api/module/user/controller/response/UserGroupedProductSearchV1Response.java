package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
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
            @Schema(description = "Default independently ranked offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String recommendedOfferKey,
            @Schema(description = "Distinct merchant, variant, and selling-plan offers", requiredMode = Schema.RequiredMode.REQUIRED)
            List<OfferResponse> offers
    ) {

        public static CanonicalProductResponse from(CanonicalProduct product) {
            return from(product, null, java.util.Map.of(), product == null ? java.util.Map.of() : product.offers().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Offer::key, UserOfferCommercialState::discovery)));
        }

        public static CanonicalProductResponse from(
                CanonicalProduct product,
                ProductRankingExplanation explanation,
                Map<String, OfferRankingExplanation> offerExplanations,
                Map<String, UserOfferCommercialState> commercialStates
        ) {
            return product == null ? null : new CanonicalProductResponse(
                    product.key(), product.title(), product.description(),
                    product.media().stream().map(ProductMediaResponse::from).toList(),
                    product.attributes().stream().map(ProductAttributeResponse::from).toList(),
                    product.materials().stream().map(ProductMaterialResponse::from).toList(),
                    product.certifications().stream().map(ProductCertificationResponse::from).toList(),
                    product.attribution().stream().map(ProductAttributionResponse::from).toList(),
                    product.identityEvidence().stream().map(ProductIdentityEvidenceResponse::from).toList(),
                    product.provenance().stream().map(ResultProvenanceResponse::from).toList(),
                    ProductRankingExplanationResponse.from(explanation),
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

    @Schema(description = "One selectable merchant, variant, and selling-plan offer")
    public record OfferResponse(
            @Schema(description = "Stable versioned offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String key,
            @Schema(description = "Provider and commercial offer identity", requiredMode = Schema.RequiredMode.REQUIRED)
            OfferIdentityResponse identity,
            @Schema(description = "Merchant display name", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchantName,
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
            return new OfferResponse(
                    offer.key(), OfferIdentityResponse.from(offer.identity()), offer.merchantName(), offer.variantTitle(),
                    MoneyResponse.from(offer.price()), MoneyResponse.from(offer.listPrice()),
                    OfferAvailabilityResponse.from(offer.availability()),
                    offer.delivery().stream().map(OfferDeliveryResponse::from).toList(), offer.checkoutUrl(),
                    offer.selectedOptions().stream().map(ProductAttributeResponse::from).toList(),
                    checkoutExperience(offer),
                    UserOfferCommercialStateResponse.from(commercialState),
                    OfferRankingExplanationResponse.from(explanation),
                    offer.provenance().stream().map(ResultProvenanceResponse::from).toList()
            );
        }

        private static CheckoutExperienceLevel checkoutExperience(Offer offer) {
            if (offer.provenance().stream().anyMatch(value -> value.localRouting() != null)
                    && Boolean.TRUE.equals(offer.rankingEvidence().checkoutCapable())) {
                return CheckoutExperienceLevel.MEANT_MANAGED;
            }
            return offer.checkoutUrl() == null
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

        static OfferIdentityResponse from(OfferIdentity identity) {
            return new OfferIdentityResponse(
                    identity.provider().value(),
                    identity.merchantScope().merchantIntegrationFallbackId(),
                    ExternalIdentifierResponse.from(identity.merchantScope().externalMerchantIdentity()),
                    OfferMerchantScopeResponse.from(identity.merchantScope()),
                    ExternalIdentifierResponse.from(identity.externalProductIdentity()),
                    ExternalIdentifierResponse.from(identity.externalVariantIdentity()),
                    identity.components().stream().map(OfferComponentIdentityResponse::from).toList(),
                    SellingPlanIdentityResponse.from(identity.sellingPlanIdentity())
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

        static OfferComponentIdentityResponse from(OfferComponentIdentity component) {
            return new OfferComponentIdentityResponse(
                    ExternalIdentifierResponse.from(component.externalProductIdentity()),
                    ExternalIdentifierResponse.from(component.externalVariantIdentity()),
                    component.quantity(),
                    component.selectedOptions().stream().map(ProductAttributeResponse::from).toList()
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

        static SellingPlanIdentityResponse from(SellingPlanIdentity identity) {
            return identity == null ? null : new SellingPlanIdentityResponse(
                    ExternalIdentifierResponse.from(identity.groupReference()),
                    ExternalIdentifierResponse.from(identity.planReference()),
                    identity.options().stream().map(SellingPlanOptionResponse::from).toList()
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

        static SellingPlanOptionResponse from(SellingPlanOption option) {
            return new SellingPlanOptionResponse(option.name(), option.value());
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

        static OfferDeliveryResponse from(OfferDelivery delivery) {
            return new OfferDeliveryResponse(
                    delivery.method(),
                    delivery.destinationRegion(),
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

        static ProductMediaResponse from(ProductMedia media) {
            return new ProductMediaResponse(media.type(), media.url(), media.altText(), media.width(), media.height());
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

        static ProductAttributeResponse from(ProductAttribute attribute) {
            return new ProductAttributeResponse(attribute.group(), attribute.name(), attribute.value());
        }
    }

    @Schema(description = "Typed product material")
    public record ProductMaterialResponse(
            @Schema(description = "Material name", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "Composition percentage in basis points", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer percentageBasisPoints
    ) {

        static ProductMaterialResponse from(ProductMaterial material) {
            return new ProductMaterialResponse(material.name(), material.percentageBasisPoints());
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

        static ProductCertificationResponse from(ProductCertification certification) {
            return new ProductCertificationResponse(
                    certification.name(),
                    certification.issuer(),
                    certification.identifier(),
                    certification.verificationUrl()
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

        static ProductAttributionResponse from(ProductAttribution attribution) {
            return new ProductAttributionResponse(
                    attribution.label(),
                    attribution.url(),
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
            @Schema(description = "Verified external merchant domain when supplied by the provider", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String externalMerchantDomain,
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
                    provenance.externalMerchantDomain(),
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
            String reference,
            @Schema(description = "Source endpoint or document URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            URI uri
    ) {

        static ResultSourceReferenceResponse from(ResultSourceReference sourceReference) {
            return new ResultSourceReferenceResponse(
                    sourceReference.type(), sourceReference.reference(), sourceReference.uri());
        }
    }
}
