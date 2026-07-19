package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
final class AgentCartSnapshotSupport {

    private final ObjectMapper objectMapper;

    CartState project(List<AgentArtifactReference> artifacts) {
        List<CartSnapshot> history = cartSnapshots(artifacts);
        List<CartSnapshot> current = currentCartSnapshots(history);
        Map<UUID, CartSnapshot> currentByCartId = new LinkedHashMap<>();
        current.forEach(snapshot -> currentByCartId.put(snapshot.cartId(), snapshot));
        return new CartState(
                history,
                current,
                mostRecentlyRemovedLine(history, currentByCartId)
        );
    }

    private List<CartSnapshot> cartSnapshots(List<AgentArtifactReference> artifacts) {
        List<CartSnapshot> snapshots = new ArrayList<>();
        for (AgentArtifactReference artifact : artifacts) {
            if (artifact.getArtifactType() != AgentArtifactType.CART || artifact.getCartId() == null) {
                continue;
            }
            JsonNode payload = readPayload(artifact);
            snapshots.add(new CartSnapshot(
                    artifact,
                    artifact.getCartId(),
                    routingScopeKey(artifact, payload),
                    cartPartitionKey(artifact, payload),
                    artifact.getLabel(),
                    cartLines(artifact, payload, artifacts)
            ));
        }
        snapshots.sort(this::compareNewestCartSnapshots);
        return List.copyOf(snapshots);
    }

    private List<CartSnapshot> currentCartSnapshots(List<CartSnapshot> history) {
        Map<String, CartSnapshot> currentByPartition = new LinkedHashMap<>();
        Set<UUID> currentCartIds = new HashSet<>();
        history.forEach(snapshot -> {
            if (currentCartIds.add(snapshot.cartId())) {
                currentByPartition.putIfAbsent(snapshot.partitionKey(), snapshot);
            }
        });
        return List.copyOf(currentByPartition.values());
    }

    private int compareNewestCartSnapshots(CartSnapshot left, CartSnapshot right) {
        return Comparator
                .comparing(
                        (CartSnapshot snapshot) -> snapshot.artifact().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        snapshot -> identifier(snapshot.artifact().getMessageId()),
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparingInt(snapshot -> snapshot.artifact().getOrdinal())
                .thenComparing(
                        snapshot -> identifier(snapshot.artifact().getId()),
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .compare(left, right);
    }

    private String identifier(UUID value) {
        return value == null ? null : value.toString();
    }

    private String routingScopeKey(AgentArtifactReference artifact, JsonNode payload) {
        String routingScopeKey = text(payload, "routingScopeKey");
        return present(routingScopeKey) ? routingScopeKey : "cart:" + artifact.getCartId();
    }

    private String cartPartitionKey(AgentArtifactReference artifact, JsonNode payload) {
        String routing = text(payload, "routingScopeKey");
        if (present(routing)) {
            return "routing:" + routing.toLowerCase(Locale.ROOT);
        }
        String integration = text(payload, "merchantIntegrationId");
        if (present(integration)) {
            return "integration:" + integration.toLowerCase(Locale.ROOT);
        }
        String merchant = text(payload, "merchantId");
        if (present(merchant)) {
            return "merchant:" + merchant.toLowerCase(Locale.ROOT);
        }
        String provider = Optional.ofNullable(text(payload, "provider")).orElse("").toLowerCase(Locale.ROOT);
        String external = text(payload, "externalMerchantId");
        if (present(external)) {
            return "external:" + provider + ":" + external.toLowerCase(Locale.ROOT);
        }
        String domain = text(payload, "merchantDomain");
        if (present(domain)) {
            return "domain:" + provider + ":" + domain.toLowerCase(Locale.ROOT);
        }
        return "cart:" + artifact.getCartId();
    }

    private List<CartLine> cartLines(
            AgentArtifactReference cart,
            JsonNode payload,
            List<AgentArtifactReference> artifacts
    ) {
        JsonNode lines = payload == null ? null : payload.get("lines");
        if (lines != null && lines.isArray()) {
            List<CartLine> parsed = new ArrayList<>();
            lines.forEach(line -> {
                UUID cartLineId = uuid(text(line, "cartLineId"));
                if (cartLineId != null) {
                    parsed.add(new CartLine(
                            cart.getCartId(),
                            cartLineId,
                            normalized(text(line, "offerKey")),
                            firstPresent(text(line, "productTitle"), text(line, "label"), "Cart item")
                    ));
                }
            });
            return List.copyOf(parsed);
        }
        return artifacts.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.CART_LINE)
                .filter(reference -> Objects.equals(reference.getCartId(), cart.getCartId()))
                .filter(reference -> sameResultSet(cart, reference))
                .filter(reference -> reference.getCartLineId() != null)
                .sorted(Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .map(reference -> new CartLine(
                        cart.getCartId(),
                        reference.getCartLineId(),
                        normalized(reference.getOfferKey()),
                        firstPresent(reference.getLabel(), "Cart item")
                ))
                .toList();
    }

    private Optional<RemovedCartLine> mostRecentlyRemovedLine(
            List<CartSnapshot> history,
            Map<UUID, CartSnapshot> currentByCartId
    ) {
        for (int newerIndex = 0; newerIndex < history.size(); newerIndex++) {
            CartSnapshot newer = history.get(newerIndex);
            CartSnapshot older = history.subList(newerIndex + 1, history.size()).stream()
                    .filter(candidate -> candidate.cartId().equals(newer.cartId()))
                    .findFirst()
                    .orElse(null);
            if (older == null) {
                continue;
            }
            List<CartLine> removed = older.lines().stream()
                    .filter(line -> line.offerKey() != null)
                    .filter(line -> newer.lines().stream().noneMatch(candidate -> sameOffer(candidate, line)))
                    .toList();
            if (removed.size() != 1) {
                continue;
            }
            CartLine line = removed.getFirst();
            CartSnapshot current = currentByCartId.get(newer.cartId());
            if (current != null
                    && current.lines().stream().noneMatch(candidate -> sameOffer(candidate, line))) {
                return Optional.of(new RemovedCartLine(current.cartId(), line));
            }
        }
        return Optional.empty();
    }

    private boolean sameOffer(CartLine left, CartLine right) {
        return left.offerKey() != null && left.offerKey().equals(right.offerKey());
    }

    private boolean sameResultSet(AgentArtifactReference left, AgentArtifactReference right) {
        if (left.getMessageId() != null || right.getMessageId() != null) {
            return Objects.equals(left.getMessageId(), right.getMessageId());
        }
        if (left.getToolInvocationId() != null || right.getToolInvocationId() != null) {
            return Objects.equals(left.getToolInvocationId(), right.getToolInvocationId());
        }
        return Objects.equals(left.getRunId(), right.getRunId())
                && Objects.equals(left.getCreatedAt(), right.getCreatedAt());
    }

    private JsonNode readPayload(AgentArtifactReference artifact) {
        try {
            return objectMapper.readTree(artifact.getPayloadJson());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalized(String value) {
        return present(value) ? value : null;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (present(value)) {
                return value;
            }
        }
        return "unknown";
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    record CartState(
            List<CartSnapshot> history,
            List<CartSnapshot> current,
            Optional<RemovedCartLine> mostRecentlyRemovedLine
    ) {
    }

    record CartSnapshot(
            AgentArtifactReference artifact,
            UUID cartId,
            String routingScopeKey,
            String partitionKey,
            String label,
            List<CartLine> lines
    ) {
    }

    record CartLine(UUID cartId, UUID cartLineId, String offerKey, String label) {
    }

    record RemovedCartLine(UUID currentCartId, CartLine line) {
    }
}
