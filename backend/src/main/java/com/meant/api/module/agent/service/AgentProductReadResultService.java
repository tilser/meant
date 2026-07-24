package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentCanonicalProductArtifact;
import com.meant.api.module.agent.service.dto.AgentCanonicalProductArtifact.AgentCanonicalOfferArtifact;
import com.meant.api.module.agent.service.dto.AgentOfferReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductVariantDetailsResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityAnchorResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityProductArtifact;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.support.CatalogBuyerPresentation;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class AgentProductReadResultService {

    private static final int DESCRIPTION_LIMIT = 600;
    private final AgentJsonSupport json;

    public AgentProductReadResultService(AgentJsonSupport json) {
        this.json = json;
    }

    public AgentProductReferenceResult reference(CanonicalProduct product, int ordinal) {
        return reference(product, ordinal, null);
    }

    public AgentProductReferenceResult reference(
            CanonicalProduct product,
            int ordinal,
            RehydratedProductDetails details
    ) {
        List<ResultProvenance> provenance = Stream.concat(
                        product.provenance().stream(),
                        product.offers().stream().flatMap(offer -> offer.provenance().stream())
                )
                .distinct()
                .toList();
        String imageUrl = product.media().stream()
                .map(media -> CatalogBuyerPresentation.safeUri(media.url(), provenance))
                .filter(value -> value != null)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
        List<AgentOfferReferenceResult> offers = product.offers().stream()
                .map(this::offer)
                .toList();
        return new AgentProductReferenceResult(
                ordinal,
                product.key(),
                CatalogBuyerPresentation.label(product.title(), provenance),
                bounded(CatalogBuyerPresentation.text(product.description(), provenance)),
                imageUrl,
                product.offers().getFirst().key(),
                offers,
                AgentProductVariantDetailsResult.from(details)
        );
    }

    public List<AgentArtifact> discoveryArtifacts(
            CanonicalProduct product,
            int ordinal,
            ProductRankingExplanation rankingExplanation,
            UserCanonicalProductPersonalizationResult personalization,
            Map<String, OfferRankingExplanation> offerRankingExplanations
    ) {
        return discoveryArtifacts(
                product,
                ordinal,
                rankingExplanation,
                personalization,
                offerRankingExplanations,
                null
        );
    }

    public List<AgentArtifact> discoveryArtifacts(
            CanonicalProduct product,
            int ordinal,
            ProductRankingExplanation rankingExplanation,
            UserCanonicalProductPersonalizationResult personalization,
            Map<String, OfferRankingExplanation> offerRankingExplanations,
            AgentSimilarityAnchorResult similarityAnchor
    ) {
        return artifacts(
                product,
                ordinal,
                AgentCanonicalProductArtifact.discovery(
                        product,
                        rankingExplanation,
                        personalization,
                        offerRankingExplanations
                ),
                similarityAnchor
        );
    }

    public List<AgentArtifact> detailArtifacts(UserProductDetailResult detail, int ordinal) {
        return artifacts(detail.product(), ordinal, AgentCanonicalProductArtifact.detail(detail), null);
    }

    private List<AgentArtifact> artifacts(
            CanonicalProduct product,
            int ordinal,
            AgentCanonicalProductArtifact payload,
            AgentSimilarityAnchorResult similarityAnchor
    ) {
        Object durablePayload = similarityAnchor == null
                ? payload
                : new AgentSimilarityProductArtifact(payload, similarityAnchor);
        AgentArtifact productArtifact = new AgentArtifact(
                AgentArtifactType.PRODUCT,
                ordinal,
                product.key(),
                payload.title(),
                product.key(),
                payload.recommendedOfferKey(),
                null,
                null,
                null,
                null,
                json.writeArtifact(durablePayload)
        );
        List<AgentArtifact> offerArtifacts = payload.offers().stream()
                .map(offer -> new AgentArtifact(
                        AgentArtifactType.OFFER,
                        ordinal,
                        offer.key(),
                        offerLabel(payload, offer),
                        product.key(),
                        offer.key(),
                        null,
                        null,
                        null,
                        null,
                        json.writeArtifact(offer)
                ))
                .toList();
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(productArtifact), offerArtifacts.stream())
                .toList();
    }

    private AgentOfferReferenceResult offer(Offer offer) {
        Money price = offer.price();
        String merchantOrigin =
                CatalogBuyerPresentation.merchantOrigin(offer.provenance());
        return new AgentOfferReferenceResult(
                offer.key(),
                merchantOrigin == null
                        ? CatalogBuyerPresentation.label(
                                offer.merchantName(),
                                offer.provenance()
                        )
                        : merchantOrigin,
                CatalogBuyerPresentation.text(offer.variantTitle(), offer.provenance()),
                price == null ? null : price.minorUnits(),
                price == null ? null : price.currency(),
                offer.availability().status(),
                offer.selectedOptions().stream()
                        .map(attribute -> sanitizedAttribute(attribute, offer.provenance()))
                        .toList()
        );
    }

    private String offerLabel(
            AgentCanonicalProductArtifact product,
            AgentCanonicalOfferArtifact offer
    ) {
        String merchant = offer.merchantOrigin() == null
                ? offer.merchantName() == null ? "offer" : offer.merchantName()
                : offer.merchantOrigin();
        return (product.title() == null ? product.key() : product.title()) + " — " + merchant;
    }

    private ProductAttribute sanitizedAttribute(
            ProductAttribute attribute,
            List<ResultProvenance> provenance
    ) {
        return new ProductAttribute(
                CatalogBuyerPresentation.label(attribute.group(), provenance),
                CatalogBuyerPresentation.label(attribute.name(), provenance),
                CatalogBuyerPresentation.label(attribute.value(), provenance)
        );
    }

    private String bounded(String value) {
        if (value == null || value.length() <= DESCRIPTION_LIMIT) {
            return value;
        }
        return value.substring(0, DESCRIPTION_LIMIT) + "…";
    }
}
