package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.DeliveryMethod;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.IdentityEvidenceStrength;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferDelivery;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCertification;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import com.meant.api.plugin.catalog.common.dto.ProductMaterial;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Version 1 grouped product-search response with canonical products and merchant offers")
public record UserGroupedProductSearchV1Response(
        @Schema(description = "Original user search query", requiredMode = Schema.RequiredMode.REQUIRED)
        String query,
        @Schema(description = "Normalized cache identity for the search", requiredMode = Schema.RequiredMode.REQUIRED)
        String normalizedQuery,
        @Schema(description = "Taste and settings profile hash used for the search", requiredMode = Schema.RequiredMode.REQUIRED)
        String profileHash,
        @Schema(description = "Whether results came from the existing flat-search cache", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cached,
        @Schema(description = "Flat-result offset used before grouping", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Flat-result page size used before grouping", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(description = "Next flat-result offset, when another page exists", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer nextOffset,
        @Schema(description = "Whether another flat-result page exists", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore,
        @Schema(description = "Deterministically ordered canonical products", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CanonicalProductResponse> products
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
                result.products().stream().map(CanonicalProductResponse::from).toList()
        );
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
            @Schema(description = "Distinct merchant, variant, and selling-plan offers", requiredMode = Schema.RequiredMode.REQUIRED)
            List<OfferResponse> offers
    ) {

        static CanonicalProductResponse from(CanonicalProduct product) {
            return new CanonicalProductResponse(
                    product.key(),
                    product.title(),
                    product.description(),
                    product.media().stream().map(ProductMediaResponse::from).toList(),
                    product.attributes().stream().map(ProductAttributeResponse::from).toList(),
                    product.materials().stream().map(ProductMaterialResponse::from).toList(),
                    product.certifications().stream().map(ProductCertificationResponse::from).toList(),
                    product.attribution().stream().map(ProductAttributionResponse::from).toList(),
                    product.identityEvidence().stream().map(ProductIdentityEvidenceResponse::from).toList(),
                    product.provenance().stream().map(ResultProvenanceResponse::from).toList(),
                    product.offers().stream().map(OfferResponse::from).toList()
            );
        }
    }

    @Schema(description = "One selectable merchant, variant, and selling-plan offer")
    public record OfferResponse(
            @Schema(description = "Stable versioned offer key", requiredMode = Schema.RequiredMode.REQUIRED)
            String key,
            @Schema(description = "Provider integration and external offer identity", requiredMode = Schema.RequiredMode.REQUIRED)
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
            @Schema(description = "Every source observation merged into this exact offer", requiredMode = Schema.RequiredMode.REQUIRED)
            List<ResultProvenanceResponse> provenance
    ) {

        static OfferResponse from(Offer offer) {
            return new OfferResponse(
                    offer.key(),
                    OfferIdentityResponse.from(offer.identity()),
                    offer.merchantName(),
                    offer.variantTitle(),
                    MoneyResponse.from(offer.price()),
                    MoneyResponse.from(offer.listPrice()),
                    OfferAvailabilityResponse.from(offer.availability()),
                    offer.delivery().stream().map(OfferDeliveryResponse::from).toList(),
                    offer.checkoutUrl(),
                    offer.selectedOptions().stream().map(ProductAttributeResponse::from).toList(),
                    offer.provenance().stream().map(ResultProvenanceResponse::from).toList()
            );
        }
    }

    @Schema(description = "Provider-defined identities that determine offer uniqueness")
    public record OfferIdentityResponse(
            @Schema(description = "Commerce provider identity", requiredMode = Schema.RequiredMode.REQUIRED)
            String provider,
            @Schema(description = "MerchantIntegration primary key", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID merchantIntegrationId,
            @Schema(description = "External merchant reference", requiredMode = Schema.RequiredMode.REQUIRED)
            ExternalIdentifierResponse externalMerchantIdentity,
            @Schema(description = "External product reference", requiredMode = Schema.RequiredMode.REQUIRED)
            ExternalIdentifierResponse externalProductIdentity,
            @Schema(description = "External variant reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            ExternalIdentifierResponse externalVariantIdentity,
            @Schema(description = "Selling-plan identity and context", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            SellingPlanIdentityResponse sellingPlanIdentity
    ) {

        static OfferIdentityResponse from(OfferIdentity identity) {
            return new OfferIdentityResponse(
                    identity.provider().value(),
                    identity.merchantIntegrationId(),
                    ExternalIdentifierResponse.from(identity.externalMerchantIdentity()),
                    ExternalIdentifierResponse.from(identity.externalProductIdentity()),
                    ExternalIdentifierResponse.from(identity.externalVariantIdentity()),
                    SellingPlanIdentityResponse.from(identity.sellingPlanIdentity())
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

    @Schema(description = "Typed product or selected-option attribute")
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

    @Schema(description = "Provider, integration, external identities, freshness, and debugging source")
    public record ResultProvenanceResponse(
            @Schema(description = "Commerce provider identity", requiredMode = Schema.RequiredMode.REQUIRED)
            String provider,
            @Schema(description = "MerchantIntegration primary key", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID merchantIntegrationId,
            @Schema(description = "External merchant reference", requiredMode = Schema.RequiredMode.REQUIRED)
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
                    provenance.merchantIntegrationId(),
                    ExternalIdentifierResponse.from(provenance.externalMerchantReference()),
                    ExternalIdentifierResponse.from(provenance.externalProductReference()),
                    ExternalIdentifierResponse.from(provenance.externalVariantReference()),
                    ResultFreshnessResponse.from(provenance.freshness()),
                    ResultSourceReferenceResponse.from(provenance.sourceReference())
            );
        }
    }

    @Schema(description = "Observation timestamp and optional source-provided freshness deadline")
    public record ResultFreshnessResponse(
            @Schema(description = "Time the source was observed", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant observedAt,
            @Schema(description = "Time after which the observation is stale", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant freshUntil
    ) {

        static ResultFreshnessResponse from(ResultFreshness freshness) {
            return new ResultFreshnessResponse(freshness.observedAt(), freshness.freshUntil());
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
