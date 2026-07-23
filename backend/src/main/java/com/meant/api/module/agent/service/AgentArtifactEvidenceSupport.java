package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.descriptiveTokens;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.literalReference;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.arrayValues;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.text;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Groups and resolves already-loaded agent artifacts without performing any repository access.
 */
final class AgentArtifactEvidenceSupport {

    private AgentArtifactEvidenceSupport() {
    }

    static List<AgentArtifactReference> latestArtifactSet(
            List<AgentArtifactReference> evidence,
            AgentArtifactType type
    ) {
        Optional<AgentArtifactReference> newest = evidence.stream()
                .filter(reference -> reference.getArtifactType() == type)
                .findFirst();
        if (newest.isEmpty()) {
            return List.of();
        }
        AgentArtifactReference anchor = newest.get();
        return evidence.stream()
                .filter(reference -> reference.getArtifactType() == type)
                .filter(reference -> sameResultSet(anchor, reference))
                .sorted(Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .toList();
    }

    static List<AgentArtifactReference> latestInventoryResultSet(
            List<AgentArtifactReference> evidence,
            ObjectMapper objectMapper
    ) {
        Optional<AgentArtifactReference> newest = evidence.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .filter(reference -> !selectedInventoryItem(reference, objectMapper))
                .findFirst();
        if (newest.isEmpty()) {
            return List.of();
        }
        AgentArtifactReference anchor = newest.get();
        return evidence.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .filter(reference -> !selectedInventoryItem(reference, objectMapper))
                .filter(reference -> sameResultSet(anchor, reference))
                .sorted(Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .toList();
    }

    static <T> Optional<T> atOrdinal(List<T> items, int ordinal) {
        return ordinal > 0 && ordinal <= items.size()
                ? Optional.of(items.get(ordinal - 1))
                : Optional.empty();
    }

    static Optional<AgentArtifactReference> soleLatestProduct(List<AgentArtifactReference> evidence) {
        List<List<AgentArtifactReference>> sets = productSets(evidence);
        return sets.isEmpty() || sets.getFirst().size() != 1
                ? Optional.empty()
                : Optional.of(sets.getFirst().getFirst());
    }

    static List<List<AgentArtifactReference>> productSets(List<AgentArtifactReference> recent) {
        List<List<AgentArtifactReference>> sets = new ArrayList<>();
        for (AgentArtifactReference reference : recent) {
            if (!isProductReference(reference)) {
                continue;
            }
            List<AgentArtifactReference> matching = sets.stream()
                    .filter(set -> sameResultSet(set.getFirst(), reference))
                    .findFirst()
                    .orElseGet(() -> {
                        List<AgentArtifactReference> created = new ArrayList<>();
                        sets.add(created);
                        return created;
                    });
            matching.add(reference);
        }
        sets.forEach(set -> set.sort(Comparator.comparingInt(AgentArtifactReference::getOrdinal)));
        return sets;
    }

    static Optional<AgentCartSnapshotSupport.CartSnapshot> currentCart(
            List<AgentArtifactReference> evidence,
            String cartId,
            AgentCartSnapshotSupport cartSnapshotSupport
    ) {
        if (cartId == null || cartId.isBlank()) {
            return Optional.empty();
        }
        return currentCartSnapshots(evidence, cartSnapshotSupport).stream()
                .filter(snapshot -> snapshot.cartId().toString().equals(cartId))
                .findFirst();
    }

    static List<AgentCartSnapshotSupport.CartLine> currentCartLines(
            List<AgentArtifactReference> evidence,
            AgentCartSnapshotSupport cartSnapshotSupport
    ) {
        return currentCartSnapshots(evidence, cartSnapshotSupport).stream()
                .flatMap(snapshot -> snapshot.lines().stream())
                .toList();
    }

    static List<AgentCartSnapshotSupport.CartSnapshot> currentCartSnapshots(
            List<AgentArtifactReference> evidence,
            AgentCartSnapshotSupport cartSnapshotSupport
    ) {
        return cartSnapshotSupport.project(evidence).current();
    }

    static Optional<AgentCartSnapshotSupport.RemovedCartLine> mostRecentlyRemovedLine(
            List<AgentArtifactReference> evidence,
            AgentCartSnapshotSupport cartSnapshotSupport
    ) {
        return cartSnapshotSupport.project(evidence).mostRecentlyRemovedLine();
    }

    static boolean matchesLatestCartSet(
            JsonNode arguments,
            List<AgentArtifactReference> evidence,
            AgentCartSnapshotSupport cartSnapshotSupport
    ) {
        Set<String> requested = arrayValues(arguments, "cartIds");
        Set<String> latest = currentCartSnapshots(evidence, cartSnapshotSupport).stream()
                .map(AgentCartSnapshotSupport.CartSnapshot::cartId)
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return !requested.isEmpty() && requested.equals(latest);
    }

    static boolean matchesSingleLatestCheckout(
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        List<AgentArtifactReference> latest = latestArtifactSet(evidence, AgentArtifactType.CHECKOUT);
        return latest.size() == 1
                && latest.getFirst().getCartId() != null
                && latest.getFirst().getCartId().toString().equals(text(arguments, "cartId"));
    }

    static boolean matchesResolvedProductReadTarget(
            AgentToolExecutionContext context,
            String canonicalArgumentsJson,
            List<AgentVisibleProductReference> candidates,
            ObjectMapper objectMapper
    ) {
        JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
        String proposedProductKey = text(arguments, "canonicalProductKey");
        if (proposedProductKey == null || proposedProductKey.isBlank()) {
            return false;
        }
        String userText = Optional.ofNullable(context.triggeringUserText()).orElse("");
        if (candidates.isEmpty()) {
            return literalReference(userText, proposedProductKey);
        }

        List<AgentVisibleProductReference> stableMatches = candidates.stream()
                .filter(product -> literalReference(userText, product.canonicalProductKey())
                        || literalReference(userText, product.recommendedOfferKey()))
                .toList();
        if (!stableMatches.isEmpty()) {
            return stableMatches.size() == 1
                    && stableMatches.getFirst().canonicalProductKey().equals(proposedProductKey);
        }

        Set<String> description = descriptiveTokens(userText);
        if (description.isEmpty()) {
            return candidates.size() == 1
                    && candidates.getFirst().canonicalProductKey().equals(proposedProductKey);
        }
        List<AgentVisibleProductReference> namedMatches = candidates.stream()
                .filter(product -> descriptiveTokens(product.title()).containsAll(description))
                .toList();
        return namedMatches.size() == 1
                && namedMatches.getFirst().canonicalProductKey().equals(proposedProductKey);
    }

    static boolean matchesResolvedInventoryReadTarget(
            AgentToolExecutionContext context,
            String canonicalArgumentsJson,
            List<AgentArtifactReference> evidence,
            ObjectMapper objectMapper
    ) {
        JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
        String proposedInventoryItemId = text(arguments, "inventoryItemId");
        if (proposedInventoryItemId == null || proposedInventoryItemId.isBlank()) {
            return false;
        }
        List<AgentArtifactReference> candidates = latestInventoryResultSet(evidence, objectMapper);
        if (candidates.isEmpty()) {
            return false;
        }
        String userText = Optional.ofNullable(context.triggeringUserText()).orElse("");
        List<AgentArtifactReference> stableMatches = candidates.stream()
                .filter(candidate -> literalReference(userText, candidate.getInventoryItemId().toString()))
                .toList();
        if (!stableMatches.isEmpty()) {
            return stableMatches.size() == 1
                    && stableMatches.getFirst().getInventoryItemId().toString()
                            .equals(proposedInventoryItemId);
        }
        Set<String> description = descriptiveTokens(userText);
        if (description.isEmpty()) {
            return candidates.size() == 1
                    && candidates.getFirst().getInventoryItemId().toString()
                            .equals(proposedInventoryItemId);
        }
        List<AgentArtifactReference> namedMatches = candidates.stream()
                .filter(candidate -> inventoryDescriptiveTokens(candidate, objectMapper).containsAll(description))
                .toList();
        return namedMatches.size() == 1
                && namedMatches.getFirst().getInventoryItemId().toString()
                        .equals(proposedInventoryItemId);
    }

    private static Set<String> inventoryDescriptiveTokens(
            AgentArtifactReference reference,
            ObjectMapper objectMapper
    ) {
        Set<String> tokens = new LinkedHashSet<>(descriptiveTokens(reference.getLabel()));
        try {
            JsonNode payload = objectMapper.readTree(reference.getPayloadJson());
            JsonNode item = payload == null ? null : payload.get("item");
            if (item == null || !item.isObject()) {
                item = payload;
            }
            if (item == null || !item.isObject()) {
                return Set.copyOf(tokens);
            }
            for (String field : List.of(
                    "name", "brand", "category", "description", "notes", "size", "color", "material",
                    "unit", "location"
            )) {
                tokens.addAll(descriptiveTokens(text(item, field)));
            }
            JsonNode attributes = item.get("attributes");
            if (attributes != null && attributes.isArray()) {
                for (JsonNode attribute : attributes) {
                    if (attribute.isTextual()) {
                        tokens.addAll(descriptiveTokens(attribute.asText()));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // The trusted display label remains usable when reading historical payloads fails.
        }
        return Set.copyOf(tokens);
    }

    private static boolean selectedInventoryItem(
            AgentArtifactReference reference,
            ObjectMapper objectMapper
    ) {
        try {
            JsonNode payload = objectMapper.readTree(reference.getPayloadJson());
            return "SELECTED_ITEM".equals(text(payload, "kind"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isProductReference(AgentArtifactReference reference) {
        return (reference.getArtifactType() == AgentArtifactType.PRODUCT
                || reference.getArtifactType() == AgentArtifactType.SAVED_PRODUCT)
                && reference.getCanonicalProductKey() != null;
    }

    static boolean sameResultSet(AgentArtifactReference left, AgentArtifactReference right) {
        if (left.getMessageId() != null || right.getMessageId() != null) {
            return Objects.equals(left.getMessageId(), right.getMessageId());
        }
        if (left.getToolInvocationId() != null || right.getToolInvocationId() != null) {
            return Objects.equals(left.getToolInvocationId(), right.getToolInvocationId());
        }
        return Objects.equals(left.getRunId(), right.getRunId())
                && Objects.equals(left.getCreatedAt(), right.getCreatedAt());
    }

}
