package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentCanonicalProductArtifact;
import com.meant.api.module.agent.service.dto.AgentOfferReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityAnchorResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityProductArtifact;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentProductReadResultService {

    private static final int DESCRIPTION_LIMIT = 600;
    private final AgentJsonSupport json;

    public AgentProductReadResultService(AgentJsonSupport json) {
        this.json = json;
    }

    public AgentProductReferenceResult reference(CanonicalProduct product, int ordinal) {
        String imageUrl = product.media().isEmpty() ? null : product.media().getFirst().url().toString();
        List<AgentOfferReferenceResult> offers = product.offers().stream()
                .map(this::offer)
                .toList();
        return new AgentProductReferenceResult(
                ordinal,
                product.key(),
                product.title(),
                bounded(product.description()),
                imageUrl,
                product.offers().getFirst().key(),
                offers
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
                product.title(),
                product.key(),
                payload.recommendedOfferKey(),
                null,
                null,
                null,
                null,
                json.writeArtifact(durablePayload)
        );
        List<AgentArtifact> offerArtifacts = product.offers().stream()
                .map(offer -> new AgentArtifact(
                        AgentArtifactType.OFFER,
                        ordinal,
                        offer.key(),
                        offerLabel(product, offer),
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
        return new AgentOfferReferenceResult(
                offer.key(),
                offer.merchantName(),
                offer.variantTitle(),
                price == null ? null : price.minorUnits(),
                price == null ? null : price.currency(),
                offer.availability().status(),
                offer.selectedOptions()
        );
    }

    private String offerLabel(CanonicalProduct product, Offer offer) {
        String merchant = offer.merchantName() == null ? "offer" : offer.merchantName();
        return (product.title() == null ? product.key() : product.title()) + " — " + merchant;
    }

    private String bounded(String value) {
        if (value == null || value.length() <= DESCRIPTION_LIMIT) {
            return value;
        }
        return value.substring(0, DESCRIPTION_LIMIT) + "…";
    }
}
