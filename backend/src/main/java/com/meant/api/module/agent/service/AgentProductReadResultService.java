package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentOfferReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import java.util.List;
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

    public List<AgentArtifact> artifacts(CanonicalProduct product, int ordinal, Object payload) {
        AgentArtifact productArtifact = new AgentArtifact(
                AgentArtifactType.PRODUCT,
                ordinal,
                product.key(),
                product.title(),
                product.key(),
                product.offers().getFirst().key(),
                null,
                null,
                null,
                null,
                json.write(payload)
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
                        json.write(offer)
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
