package com.meant.api.module.catalog.service;

import static com.meant.api.module.catalog.service.support.ProductIdentityNormalizationSupport.token;

import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProductIdentityContradictionKind;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidence;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Extracts only explicit variant/configuration facts used as conservative grouping vetoes. */
final class ProductIdentityCompatibility {

    List<ProductIdentityContradictionKind> contradictions(ProductCandidate left, ProductCandidate right) {
        EnumMap<ProductIdentityContradictionKind, Set<String>> leftFacts = facts(left);
        EnumMap<ProductIdentityContradictionKind, Set<String>> rightFacts = facts(right);
        List<ProductIdentityContradictionKind> contradictions = new ArrayList<>();
        for (ProductIdentityContradictionKind kind : ProductIdentityContradictionKind.values()) {
            Set<String> first = leftFacts.getOrDefault(kind, Set.of());
            Set<String> second = rightFacts.getOrDefault(kind, Set.of());
            if (!first.isEmpty() && !second.isEmpty() && first.stream().noneMatch(second::contains)) {
                contradictions.add(kind);
            }
        }
        return List.copyOf(contradictions);
    }

    String fingerprint(List<ProductCandidate> candidates) {
        return candidates.stream()
                .flatMap(candidate -> facts(candidate).entrySet().stream())
                .flatMap(entry -> entry.getValue().stream()
                        .map(value -> entry.getKey().name() + ":" + value))
                .distinct()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private EnumMap<ProductIdentityContradictionKind, Set<String>> facts(ProductCandidate candidate) {
        EnumMap<ProductIdentityContradictionKind, Set<String>> facts =
                new EnumMap<>(ProductIdentityContradictionKind.class);
        java.util.stream.Stream.concat(candidate.attributes().stream(), candidate.offer().selectedOptions().stream())
                .forEach(attribute -> addAttributeFact(facts, attribute));
        addBrandModelFacts(facts, candidate.identityEvidence());
        addBundleFacts(facts, candidate.offer().identity().components());
        addSellingPlanFacts(facts, candidate.offer().identity().sellingPlanIdentity());
        return facts;
    }

    private void addAttributeFact(
            EnumMap<ProductIdentityContradictionKind, Set<String>> facts,
            ProductAttribute attribute
    ) {
        ProductIdentityContradictionKind kind = switch (token(attribute.name())) {
            case "size", "shoesize", "variantsize" -> ProductIdentityContradictionKind.SIZE;
            case "color", "colour", "variantcolor", "variantcolour" -> ProductIdentityContradictionKind.COLOR;
            case "pack", "packcount", "packquantity", "unitcount" -> ProductIdentityContradictionKind.PACK_QUANTITY;
            case "generation", "modelyear", "version" -> ProductIdentityContradictionKind.GENERATION;
            case "model", "modelnumber", "mpn", "manufacturerpartnumber" -> ProductIdentityContradictionKind.MODEL;
            default -> null;
        };
        if (kind != null) {
            facts.computeIfAbsent(kind, ignored -> new HashSet<>()).add(token(attribute.value()));
        }
    }

    private void addBrandModelFacts(
            EnumMap<ProductIdentityContradictionKind, Set<String>> facts,
            List<ProductIdentityEvidence> evidence
    ) {
        evidence.stream()
                .filter(value -> value.kind() == ProductIdentityEvidenceKind.BRAND_MPN)
                .flatMap(value -> value.identifiers().stream())
                .filter(identifier -> identifier.type() == ExternalIdentifierType.MPN)
                .map(ExternalIdentifier::value)
                .map(com.meant.api.module.catalog.service.support.ProductIdentityNormalizationSupport::token)
                .forEach(value -> facts.computeIfAbsent(
                        ProductIdentityContradictionKind.MODEL,
                        ignored -> new HashSet<>()
                ).add(value));
    }

    private void addBundleFacts(
            EnumMap<ProductIdentityContradictionKind, Set<String>> facts,
            List<OfferComponentIdentity> components
    ) {
        if (components.isEmpty()) {
            facts.put(ProductIdentityContradictionKind.BUNDLE, Set.of("single"));
            return;
        }
        String shape = components.stream()
                .map(component -> identifierText(component.externalProductIdentity())
                        + ":" + identifierText(component.externalVariantIdentity())
                        + ":" + component.quantity()
                        + ":" + component.selectedOptions().stream()
                                .map(option -> token(option.name()) + "=" + token(option.value()))
                                .sorted()
                                .collect(Collectors.joining(",")))
                .sorted()
                .collect(Collectors.joining(",", "bundle:", ""));
        facts.put(ProductIdentityContradictionKind.BUNDLE, Set.of(shape));
    }

    private String identifierText(ExternalIdentifier identifier) {
        return identifier == null
                ? ""
                : identifier.type() + ":" + (identifier.namespace() == null ? "" : identifier.namespace())
                        + ":" + identifier.value();
    }

    private void addSellingPlanFacts(
            EnumMap<ProductIdentityContradictionKind, Set<String>> facts,
            SellingPlanIdentity plan
    ) {
        if (plan == null) {
            facts.put(ProductIdentityContradictionKind.SELLING_PLAN, Set.of("one-time"));
            return;
        }
        String value = java.util.stream.Stream.of(plan.groupReference(), plan.planReference())
                .filter(java.util.Objects::nonNull)
                .map(identifier -> (identifier.namespace() == null ? "" : identifier.namespace())
                        + ":" + identifier.value())
                .sorted()
                .collect(Collectors.joining("|"));
        facts.put(ProductIdentityContradictionKind.SELLING_PLAN, Set.of("plan:" + value));
    }
}
