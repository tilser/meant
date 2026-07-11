package com.meant.api.module.catalog.service.dto;

import java.util.List;

/** Provider-neutral shared product facts with all distinct merchant offers and observations. */
public record CanonicalProduct(
        String key,
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
        List<Offer> offers
) {

    public CanonicalProduct {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Canonical product key must not be blank");
        }
        key = key.trim();
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
        offers = immutable(offers);
        if (offers.isEmpty() || provenance.isEmpty()) {
            throw new IllegalArgumentException("Canonical product needs offers and provenance");
        }
    }

    public CanonicalProduct(
            String key,
            String title,
            String description,
            List<ProductMedia> media,
            List<ProductAttribute> attributes,
            List<ProductMaterial> materials,
            List<ProductCertification> certifications,
            List<ProductAttribution> attribution,
            List<ProductIdentityEvidence> identityEvidence,
            List<ResultProvenance> provenance,
            List<Offer> offers
    ) {
        this(
                key,
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
                offers
        );
    }

    public CanonicalProduct withOffers(List<Offer> rankedOffers) {
        return new CanonicalProduct(
                key,
                title,
                description,
                media,
                attributes,
                materials,
                certifications,
                attribution,
                identityEvidence,
                provenance,
                retrievalSignals,
                rankedOffers
        );
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
