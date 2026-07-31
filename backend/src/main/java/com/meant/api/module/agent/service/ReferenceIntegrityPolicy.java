package com.meant.api.module.agent.service;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates that model-supplied IDs were issued by this server for the owned conversation. */
@Service
@RequiredArgsConstructor
public class ReferenceIntegrityPolicy {

    private static final Map<String, ReferenceKind> REFERENCE_FIELDS = Map.ofEntries(
            Map.entry("offerKey", ReferenceKind.OFFER),
            Map.entry("offerKeys", ReferenceKind.OFFER),
            Map.entry("selectedOfferKey", ReferenceKind.OFFER),
            Map.entry("canonicalProductKey", ReferenceKind.PRODUCT),
            Map.entry("canonicalProductKeys", ReferenceKind.PRODUCT),
            Map.entry("inventoryItemId", ReferenceKind.INVENTORY_ITEM),
            Map.entry("inventoryItemIds", ReferenceKind.INVENTORY_ITEM),
            Map.entry("cartId", ReferenceKind.CURRENT_CART),
            Map.entry("cartIds", ReferenceKind.CURRENT_CART),
            Map.entry("cartLineId", ReferenceKind.CURRENT_CART_LINE),
            Map.entry("cartLineIds", ReferenceKind.CURRENT_CART_LINE),
            Map.entry("missionId", ReferenceKind.MISSION)
    );

    private final AgentConversationRepository conversationRepository;
    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentCartSnapshotSupport cartSnapshotSupport;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Validation validate(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson
    ) {
        if (context == null
                || context.userId() == null
                || context.conversationId() == null
                || conversationRepository.findByIdAndUserId(context.conversationId(), context.userId()).isEmpty()) {
            return Validation.rejected("reference_ownership_mismatch", "conversationId");
        }
        List<AgentArtifactReference> artifacts = artifactRepository
                .findByConversationIdOrderByCreatedAtAscOrdinalAsc(context.conversationId());
        ReferenceIndex index = ReferenceIndex.from(artifacts, cartSnapshotSupport.project(artifacts));
        JsonNode arguments;
        try {
            arguments = objectMapper.readTree(canonicalArgumentsJson);
        } catch (RuntimeException exception) {
            return Validation.rejected("invalid_arguments", null);
        }
        List<ReferenceValue> references = new ArrayList<>();
        collect(arguments, references);
        for (ReferenceValue reference : references) {
            if (!index.contains(reference.kind(), reference.value())) {
                return Validation.rejected(
                        reference.kind().currentOnly ? "stale_reference" : "reference_not_found",
                        reference.field()
                );
            }
        }
        if (!currentCartLineBelongsToSuppliedCart(arguments, index)) {
            return Validation.rejected("reference_relationship_mismatch", "cartLineId");
        }
        return Validation.ok();
    }

    private void collect(JsonNode node, List<ReferenceValue> references) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collect(child, references));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            ReferenceKind kind = REFERENCE_FIELDS.get(entry.getKey());
            if (kind != null) {
                collectField(entry.getKey(), kind, entry.getValue(), references);
            } else {
                collect(entry.getValue(), references);
            }
        });
    }

    private void collectField(
            String field,
            ReferenceKind kind,
            JsonNode node,
            List<ReferenceValue> references
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collectField(field, kind, value, references));
            return;
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            references.add(new ReferenceValue(field, kind, node.asText().trim()));
        }
    }

    private boolean currentCartLineBelongsToSuppliedCart(JsonNode arguments, ReferenceIndex index) {
        if (arguments == null || !arguments.isObject()) {
            return true;
        }
        JsonNode cartId = arguments.get("cartId");
        JsonNode cartLineId = arguments.get("cartLineId");
        if (cartId == null || cartLineId == null || !cartId.isTextual() || !cartLineId.isTextual()) {
            return true;
        }
        return Objects.equals(index.cartByLine().get(cartLineId.asText()), cartId.asText());
    }

    public record Validation(boolean accepted, String code, String field) {

        public static Validation ok() {
            return new Validation(true, null, null);
        }

        public static Validation rejected(String code, String field) {
            return new Validation(false, code, field);
        }
    }

    private record ReferenceValue(String field, ReferenceKind kind, String value) {
    }

    private enum ReferenceKind {
        OFFER(false),
        PRODUCT(false),
        INVENTORY_ITEM(false),
        CURRENT_CART(true),
        CURRENT_CART_LINE(true),
        MISSION(false);

        private final boolean currentOnly;

        ReferenceKind(boolean currentOnly) {
            this.currentOnly = currentOnly;
        }
    }

    private record ReferenceIndex(
            Set<String> offers,
            Set<String> products,
            Set<String> inventoryItems,
            Set<String> currentCarts,
            Set<String> currentCartLines,
            Set<String> missions,
            Map<String, String> cartByLine
    ) {

        private static ReferenceIndex from(
                List<AgentArtifactReference> artifacts,
                AgentCartSnapshotSupport.CartState cartState
        ) {
            Set<String> offers = values(artifacts, AgentArtifactReference::getOfferKey);
            Set<String> products = values(artifacts, AgentArtifactReference::getCanonicalProductKey);
            Set<String> inventoryItems = artifacts.stream()
                    .map(AgentArtifactReference::getInventoryItemId)
                    .filter(Objects::nonNull)
                    .map(UUID::toString)
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> missions = artifacts.stream()
                    .map(AgentArtifactReference::getStableKey)
                    .filter(Objects::nonNull)
                    .filter(value -> value.startsWith("mission:"))
                    .map(value -> value.substring("mission:".length()))
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> currentCarts = cartState.current().stream()
                    .map(snapshot -> snapshot.cartId().toString())
                    .collect(Collectors.toUnmodifiableSet());
            Map<String, String> cartByLine = cartState.current().stream()
                    .flatMap(snapshot -> snapshot.lines().stream()
                            .map(line -> Map.entry(line.cartLineId().toString(), snapshot.cartId().toString())))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            return new ReferenceIndex(
                    offers,
                    products,
                    inventoryItems,
                    currentCarts,
                    Set.copyOf(cartByLine.keySet()),
                    missions,
                    cartByLine
            );
        }

        private static Set<String> values(
                List<AgentArtifactReference> artifacts,
                java.util.function.Function<AgentArtifactReference, String> extractor
        ) {
            return artifacts.stream()
                    .map(extractor)
                    .filter(Objects::nonNull)
                    .filter(Predicate.not(String::isBlank))
                    .collect(Collectors.toUnmodifiableSet());
        }

        private boolean contains(ReferenceKind kind, String value) {
            return switch (kind) {
                case OFFER -> offers.contains(value);
                case PRODUCT -> products.contains(value);
                case INVENTORY_ITEM -> inventoryItems.contains(value);
                case CURRENT_CART -> currentCarts.contains(value);
                case CURRENT_CART_LINE -> currentCartLines.contains(value);
                case MISSION -> missions.contains(value);
            };
        }
    }
}
