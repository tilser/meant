package com.meant.api.module.catalog.service;

import static com.meant.api.module.catalog.service.support.ProductIdentityNormalizationSupport.canonicalUrl;
import static com.meant.api.module.catalog.service.support.ProductIdentityNormalizationSupport.token;
import static com.meant.api.module.catalog.service.support.ProductIdentityNormalizationSupport.typedStandardIdentifier;
import static com.meant.api.module.catalog.service.support.ProductIdentityNormalizationSupport.universalTradeItemNumber;

import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecisionReason;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidence;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind;
import com.meant.api.module.catalog.service.support.CanonicalCommerceKey;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Converts typed source evidence into deterministic comparison signals without changing the evidence. */
final class ProductIdentitySignalExtractor {

    List<ProductIdentitySignal> signals(ProductCandidate candidate) {
        return candidate.identityEvidence().stream()
                .flatMap(evidence -> signals(evidence).stream())
                .map(signal -> candidateScoped(signal, candidate))
                .sorted(ProductIdentitySignal.ORDER)
                .toList();
    }

    private ProductIdentitySignal candidateScoped(ProductIdentitySignal signal, ProductCandidate candidate) {
        String scopedKey = switch (signal.kind()) {
            case CANONICAL_URL -> signal.key() + ":merchant:" + CanonicalCommerceKey.merchantScopeKey(
                    candidate.offer().identity().merchantScope());
            case UPID -> signal.key() + ":authority:" + CanonicalCommerceKey.upidAuthorityScopeKey(
                    candidate.offer().identity().provider(), signal.evidence().sourceReference());
            default -> signal.key();
        };
        return new ProductIdentitySignal(
                scopedKey,
                signal.kind(),
                signal.evidence(),
                signal.precedence(),
                signal.reason()
        );
    }

    private List<ProductIdentitySignal> signals(ProductIdentityEvidence evidence) {
        return switch (evidence.kind()) {
            case GTIN, UPC, EAN -> evidence.identifiers().stream()
                    .filter(identifier -> identifier.type() == ExternalIdentifierType.GTIN
                            || identifier.type() == ExternalIdentifierType.UPC
                            || identifier.type() == ExternalIdentifierType.EAN)
                    .map(ExternalIdentifier::value)
                    .map(value -> universalTradeItemNumber(evidence.kind(), value))
                    .flatMap(Optional::stream)
                    .map(value -> signal("universal:" + value, evidence, 1,
                            ProductGroupingDecisionReason.UNIVERSAL_IDENTIFIER))
                    .toList();
            case UNIVERSAL_PRODUCT_ID -> typedIdentifiers(evidence, ExternalIdentifierType.UNIVERSAL_PRODUCT_ID)
                    .map(identifier -> typedStandardIdentifier(identifier).map(value -> signal(
                            "standard:" + value,
                            evidence,
                            1,
                            ProductGroupingDecisionReason.UNIVERSAL_IDENTIFIER
                    )))
                    .flatMap(Optional::stream)
                    .toList();
            case UPID -> typedIdentifiers(evidence, ExternalIdentifierType.UPID)
                    .map(identifier -> signal(
                            "upid:" + nullToEmpty(identifier.namespace()) + ":" + identifier.value(),
                            evidence,
                            0,
                            ProductGroupingDecisionReason.TRUSTED_PROVIDER_GROUP
                    ))
                    .toList();
            case BRAND_MPN -> brandModelSignal(evidence).stream().toList();
            case PROVIDER_GROUPING_ID -> typedIdentifiers(evidence, ExternalIdentifierType.PROVIDER_GROUPING)
                    .map(identifier -> signal(
                            "mapping:" + nullToEmpty(identifier.namespace()) + ":" + identifier.value(),
                            evidence,
                            3,
                            ProductGroupingDecisionReason.VERIFIED_PROVIDER_MAPPING
                    ))
                    .toList();
            case CANONICAL_URL -> typedIdentifiers(evidence, ExternalIdentifierType.CANONICAL_URL)
                    .map(ExternalIdentifier::value)
                    .map(value -> canonicalUrl(value))
                    .flatMap(Optional::stream)
                    .map(value -> signal("url:" + value, evidence, 4,
                            ProductGroupingDecisionReason.SAME_MERCHANT_CANONICAL_URL))
                    .toList();
            case SEMANTIC -> typedIdentifiers(evidence, ExternalIdentifierType.SEMANTIC_FINGERPRINT)
                    .map(identifier -> semanticKey(identifier, evidence))
                    .flatMap(Optional::stream)
                    .map(value -> signal("semantic:" + value, evidence, 5,
                            ProductGroupingDecisionReason.SEMANTIC_EVIDENCE_ONLY))
                    .toList();
        };
    }

    Stream<ExternalIdentifier> typedIdentifiers(
            ProductIdentityEvidence evidence,
            ExternalIdentifierType type
    ) {
        return evidence.identifiers().stream().filter(identifier -> identifier.type() == type);
    }

    private Optional<ProductIdentitySignal> brandModelSignal(ProductIdentityEvidence evidence) {
        String brand = firstIdentifier(evidence, ExternalIdentifierType.BRAND);
        String model = firstIdentifier(evidence, ExternalIdentifierType.MPN);
        String normalizedBrand = token(brand);
        String normalizedModel = token(model);
        if (normalizedBrand.isEmpty() || normalizedModel.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(signal(
                "brand-model:" + normalizedBrand + ":" + normalizedModel,
                evidence,
                2,
                ProductGroupingDecisionReason.VERIFIED_BRAND_MODEL
        ));
    }

    private Optional<String> semanticKey(
            ExternalIdentifier identifier,
            ProductIdentityEvidence evidence
    ) {
        String value = token(identifier.value());
        String authority = token(identifier.namespace());
        if (authority.isEmpty()) {
            authority = token(evidence.sourceReference().reference());
        }
        return authority.isEmpty() || value.isEmpty()
                ? Optional.empty()
                : Optional.of(authority + ":" + value);
    }

    private String firstIdentifier(ProductIdentityEvidence evidence, ExternalIdentifierType type) {
        return typedIdentifiers(evidence, type).map(ExternalIdentifier::value).findFirst().orElse(null);
    }

    private ProductIdentitySignal signal(
            String key,
            ProductIdentityEvidence evidence,
            int precedence,
            ProductGroupingDecisionReason reason
    ) {
        return new ProductIdentitySignal(key, evidence.kind(), evidence, precedence, reason);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
