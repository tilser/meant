package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey;
import java.util.List;

/** One source's product facts paired with its selectable merchant offer. */
public record ProductCandidate(
        String title,
        String description,
        List<ProductMedia> media,
        List<ProductAttribute> attributes,
        List<ProductMaterial> materials,
        List<ProductCertification> certifications,
        List<ProductAttribution> attribution,
        List<ProductIdentityEvidence> identityEvidence,
        List<ResultProvenance> provenance,
        List<ProductRetrievalSignal> retrievalSignals,
        Offer offer
) {

    public ProductCandidate {
        title = trimToNull(title);
        description = trimToNull(description);
        media = immutable(media);
        attributes = immutable(attributes);
        materials = immutable(materials);
        certifications = immutable(certifications);
        attribution = immutable(attribution);
        identityEvidence = immutable(identityEvidence);
        provenance = immutable(provenance);
        retrievalSignals = immutable(retrievalSignals);
        if (offer == null || provenance.isEmpty()) {
            throw new IllegalArgumentException("Product candidate needs an offer and provenance");
        }
    }

    public ProductCandidate(
            String title,
            String description,
            List<ProductMedia> media,
            List<ProductAttribute> attributes,
            List<ProductMaterial> materials,
            List<ProductCertification> certifications,
            List<ProductAttribution> attribution,
            List<ProductIdentityEvidence> identityEvidence,
            List<ResultProvenance> provenance,
            Offer offer
    ) {
        this(
                title,
                description,
                media,
                attributes,
                materials,
                certifications,
                attribution,
                identityEvidence,
                provenance,
                List.of(),
                offer
        );
    }

    public String fallbackProductKey() {
        return CanonicalCommerceKey.fallbackProductKey(offer.identity());
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
