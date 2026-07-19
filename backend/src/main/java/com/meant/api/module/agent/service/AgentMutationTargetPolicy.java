package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AgentMutationTargetPolicy {

    private static final int REFERENCE_WINDOW = 200;
    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|"
                    + "1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FIRST_COUNT = Pattern.compile(
            "\\bfirst\\s+(two|three|four|2|3|4)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMERIC_ORDINAL_SELECTION = Pattern.compile(
            "^(?:the\\s+)?(10|[1-9])(?:\\s+(?:one|item|line))?\\s*[.!]?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTEXTUAL_REFERENCE = Pattern.compile(
            "\\b(?:it|that|this|one|item|product|them|those|these)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern READD_REFERENCE = Pattern.compile(
            "\\bre-?add\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> NON_DESCRIPTIVE_TOKENS = Set.of(
            "actually", "add", "again", "already", "also", "and", "back", "bag", "basket", "both", "buy",
            "can", "cart", "check", "checkout", "choose", "compare", "could", "delete", "did", "does", "don",
            "dont", "eighth",
            "fifth", "first", "for", "fourth",
            "details", "find", "four", "from", "get", "give", "how", "inspect", "into", "item", "items",
            "look", "looking", "more", "need", "new", "not", "one", "ones",
            "ninth", "know", "like", "may", "might", "must", "now", "okay", "our",
            "order", "pair", "pin", "place", "please", "product", "products", "purchase", "put", "remove",
            "pick", "really", "recommend", "recommended", "review", "reviews", "same", "save", "second",
            "select", "selected", "seventh", "should", "show", "similar", "sixth", "size", "take", "tell",
            "tenth", "that", "the", "them", "then", "these", "third", "this", "those", "three", "two",
            "view", "want", "watch", "what", "which", "will", "with", "would", "you", "your"
    );

    private final AgentArtifactReferenceRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final AgentCartSnapshotSupport cartSnapshotSupport;

    /**
     * Binds an explicit ordinal in the user's current turn to the latest compatible result set.
     * Stable IDs still pass their normal ownership/reference validation in the tool adapter.
     */
    public boolean matchesExplicitOrdinal(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson
    ) {
        if (context == null || context.runId() == null) {
            return true;
        }
        List<Integer> ordinals = ordinals(context.triggeringUserText());
        if (ordinals.isEmpty()) {
            return true;
        }
        try {
            List<AgentArtifactReference> evidence = recent(context.conversationId());
            return matchesOrdinals(context, toolName, canonicalArgumentsJson, ordinals, evidence);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Binds model-driven mutations to an explicit ordinal, unique description, or unambiguous current conversation
     * state. Direct CTA actions bypass this policy because their target comes from the clicked component.
     */
    public boolean matchesMutationTarget(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson
    ) {
        if (context == null || context.runId() == null) {
            return true;
        }
        try {
            List<Integer> ordinals = ordinals(context.triggeringUserText());
            List<AgentArtifactReference> evidence = recent(context.conversationId());
            if (!ordinals.isEmpty()) {
                return matchesOrdinals(context, toolName, canonicalArgumentsJson, ordinals, evidence);
            }
            return matchesLiteralReference(context, toolName, canonicalArgumentsJson, evidence);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Limits broad READY-mission delegation to the selections and carts persisted on that owned mission. */
    public boolean matchesDelegatedMission(
            ShoppingMission mission,
            String toolName,
            String canonicalArgumentsJson
    ) {
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            Set<String> selectedOffers = selectedOfferKeys(mission);
            Set<String> missionCartIds = stringValues(mission.getCartReferencesJson());
            return switch (toolName) {
                case "prepare_carts" -> {
                    Set<String> requested = arrayField(arguments, "offers", "offerKey");
                    yield !requested.isEmpty() && selectedOffers.containsAll(requested);
                }
                case "add_cart_line" -> selectedOffers.contains(text(arguments, "offerKey"))
                        && missionCartIds.contains(text(arguments, "cartId"));
                case "update_cart_line", "remove_cart_line" ->
                        missionCartIds.contains(text(arguments, "cartId"));
                case "prepare_checkout" -> {
                    Set<String> requested = arrayValues(arguments, "cartIds");
                    yield !requested.isEmpty() && missionCartIds.containsAll(requested);
                }
                case "update_checkout" -> missionCartIds.contains(text(arguments, "cartId"))
                        && !stringValues(mission.getCheckoutReferencesJson()).isEmpty();
                default -> false;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesOrdinals(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson,
            List<Integer> ordinals,
            List<AgentArtifactReference> evidence
    ) {
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            return switch (toolName) {
                case "pin_product", "unpin_product", "watch_product", "unwatch_product" ->
                        single(ordinals) && expectedProduct(context, evidence, ordinals.getFirst())
                                .map(expected -> expected.getCanonicalProductKey()
                                        .equals(text(arguments, "canonicalProductKey")))
                                .orElse(false);
                case "prepare_carts" -> !mustUseCurrentCartForReadd(context, evidence)
                        && matchesPreparedOffers(context, ordinals, arguments, evidence);
                case "add_cart_line" -> single(ordinals)
                        && expectedProduct(context, evidence, ordinals.getFirst())
                        .map(expected -> offerBelongsToProduct(
                                context.conversationId(),
                                text(arguments, "offerKey"),
                                expected.getCanonicalProductKey()
                        ))
                        .orElse(false)
                        && currentCart(evidence, text(arguments, "cartId")).isPresent();
                case "update_cart_line", "remove_cart_line" -> single(ordinals)
                        && expectedCartLine(evidence, ordinals.getFirst())
                        .map(expected -> expected.cartLineId().toString().equals(text(arguments, "cartLineId"))
                                && expected.cartId().toString().equals(text(arguments, "cartId")))
                        .orElse(false);
                case "prepare_checkout" -> matchesCartIds(context, ordinals, arguments, evidence);
                case "update_checkout" -> single(ordinals)
                        && expectedCheckout(context, evidence, ordinals.getFirst())
                        .map(expected -> expected.getCartId().toString().equals(text(arguments, "cartId")))
                        .orElse(false);
                case "compare_products" ->
                        matchesProductKeys(context, ordinals, arguments, "canonicalProductKeys", evidence);
                case "pick_recommended_product" ->
                        matchesProductKeys(context, ordinals, arguments, "canonicalProductKeys", evidence);
                case "get_product", "get_product_reviews", "find_discount_codes" ->
                        single(ordinals) && expectedProduct(context, evidence, ordinals.getFirst())
                                .map(expected -> expected.getCanonicalProductKey()
                                        .equals(text(arguments, "canonicalProductKey")))
                                .orElse(false);
                case "find_similar_products" -> matchesSimilarAnchor(context, ordinals, arguments, evidence);
                default -> true;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesLiteralReference(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson,
            List<AgentArtifactReference> evidence
    ) {
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            String turn = context.triggeringUserText();
            return switch (toolName) {
                case "pin_product", "unpin_product", "watch_product", "unwatch_product" ->
                        literalReference(turn, text(arguments, "canonicalProductKey"))
                                || matchesNamedProduct(context, text(arguments, "canonicalProductKey"), evidence);
                case "prepare_carts" -> !mustUseCurrentCartForReadd(context, evidence)
                        && (allLiteral(turn, arrayField(arguments, "offers", "offerKey"))
                        || matchesNamedPreparedOffer(context, arguments, evidence)
                        || matchesContextualPreparedOffer(context, arguments, evidence));
                case "add_cart_line" -> matchesContextualCartAddition(context, arguments, evidence);
                case "update_cart_line", "remove_cart_line" ->
                        matchesContextualCartLine(context, arguments, evidence);
                case "prepare_checkout" -> matchesLatestCartSet(arguments, evidence);
                case "update_checkout" -> matchesSingleLatestCheckout(arguments, evidence);
                default -> true;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesNamedProduct(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            List<AgentArtifactReference> evidence
    ) {
        return uniquelyMentionedProduct(context, evidence)
                .map(reference -> reference.getCanonicalProductKey().equals(canonicalProductKey))
                .orElse(false);
    }

    private boolean matchesNamedPreparedOffer(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        JsonNode offers = arguments == null ? null : arguments.get("offers");
        if (offers == null || !offers.isArray() || offers.size() != 1) {
            return false;
        }
        String offerKey = text(offers.get(0), "offerKey");
        if (offerKey == null || offerKey.isBlank()) {
            return false;
        }
        return uniquelyMentionedProduct(context, evidence)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false);
    }

    private boolean matchesContextualPreparedOffer(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        JsonNode offers = arguments == null ? null : arguments.get("offers");
        String turn = Optional.ofNullable(context.triggeringUserText()).orElse("");
        if (offers == null || !offers.isArray() || offers.size() != 1
                || !CONTEXTUAL_REFERENCE.matcher(turn).find()) {
            return false;
        }
        String offerKey = text(offers.get(0), "offerKey");
        return soleLatestProduct(evidence)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false);
    }

    private boolean matchesContextualCartAddition(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        String cartId = text(arguments, "cartId");
        String offerKey = text(arguments, "offerKey");
        if (currentCart(evidence, cartId).isEmpty() || offerKey == null || offerKey.isBlank()) {
            return false;
        }
        String turn = Optional.ofNullable(context.triggeringUserText()).orElse("");
        if (isReaddReference(turn)) {
            return mostRecentlyRemovedLine(evidence)
                    .filter(removed -> removed.currentCartId().toString().equals(cartId))
                    .map(AgentCartSnapshotSupport.RemovedCartLine::line)
                    .map(AgentCartSnapshotSupport.CartLine::offerKey)
                    .filter(Objects::nonNull)
                    .filter(offerKey::equals)
                    .isPresent();
        }
        if (literalReference(turn, offerKey)) {
            return true;
        }
        if (uniquelyMentionedProduct(context, evidence)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false)) {
            return true;
        }
        if (CONTEXTUAL_REFERENCE.matcher(turn).find()
                && soleLatestProduct(evidence)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false)) {
            return true;
        }
        return false;
    }

    private boolean matchesContextualCartLine(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        String cartId = text(arguments, "cartId");
        String cartLineId = text(arguments, "cartLineId");
        List<AgentCartSnapshotSupport.CartLine> currentLines = currentCartLines(evidence);
        Optional<AgentCartSnapshotSupport.CartLine> proposed = currentLines.stream()
                .filter(line -> line.cartId().toString().equals(cartId))
                .filter(line -> line.cartLineId().toString().equals(cartLineId))
                .findFirst();
        if (proposed.isEmpty()) {
            return false;
        }
        if (literalReference(context.triggeringUserText(), cartLineId)) {
            return true;
        }
        Optional<AgentCartSnapshotSupport.CartLine> named = uniquelyMentionedCartLine(context, currentLines);
        if (named.isPresent()) {
            return named.get().equals(proposed.get());
        }
        return descriptiveTokens(context.triggeringUserText()).isEmpty()
                && CONTEXTUAL_REFERENCE.matcher(
                        Optional.ofNullable(context.triggeringUserText()).orElse("")).find()
                && currentLines.size() == 1
                && currentLines.getFirst().equals(proposed.get());
    }

    /** Resolves descriptive follow-ups to one exact label match in the newest compatible result set. */
    private Optional<AgentArtifactReference> uniquelyMentionedProduct(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence
    ) {
        Set<String> turnTokens = descriptiveTokens(context.triggeringUserText());
        if (turnTokens.isEmpty()) {
            return Optional.empty();
        }
        for (List<AgentArtifactReference> productSet : productSets(evidence)) {
            List<AgentArtifactReference> matches = productSet.stream()
                    .filter(reference -> descriptiveTokens(reference.getLabel()).containsAll(turnTokens))
                    .toList();
            if (matches.isEmpty()) {
                continue;
            }
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<AgentCartSnapshotSupport.CartLine> uniquelyMentionedCartLine(
            AgentToolExecutionContext context,
            List<AgentCartSnapshotSupport.CartLine> candidates
    ) {
        Set<String> turnTokens = descriptiveTokens(context.triggeringUserText());
        if (turnTokens.isEmpty()) {
            return Optional.empty();
        }
        List<AgentCartSnapshotSupport.CartLine> matches = candidates.stream()
                .filter(line -> descriptiveTokens(line.matchingText()).containsAll(turnTokens))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private Set<String> descriptiveTokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[’']s\\b", "")
                .replace("’", "")
                .replace("'", "");
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            String rawToken = matcher.group();
            if (NON_DESCRIPTIVE_TOKENS.contains(rawToken)) {
                continue;
            }
            String token = singularToken(rawToken);
            if (token.length() >= 3 && !NON_DESCRIPTIVE_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return Set.copyOf(tokens);
    }

    private String singularToken(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && (token.endsWith("sses")
                || token.endsWith("xes")
                || token.endsWith("zes")
                || token.endsWith("ches")
                || token.endsWith("shes"))) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private int overlap(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }

    private boolean matchesPreparedOffers(
            AgentToolExecutionContext context,
            List<Integer> ordinals,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        JsonNode offers = arguments.get("offers");
        if (offers == null || !offers.isArray() || offers.size() != ordinals.size()) {
            return false;
        }
        for (int index = 0; index < ordinals.size(); index++) {
            String offerKey = text(offers.get(index), "offerKey");
            boolean matches = expectedProduct(context, evidence, ordinals.get(index))
                    .map(expected -> offerBelongsToProduct(
                            context.conversationId(), offerKey, expected.getCanonicalProductKey()))
                    .orElse(false);
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCartIds(
            AgentToolExecutionContext context,
            List<Integer> ordinals,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        JsonNode cartIds = arguments.get("cartIds");
        if (cartIds == null || !cartIds.isArray() || cartIds.size() != ordinals.size()) {
            return false;
        }
        for (int index = 0; index < ordinals.size(); index++) {
            String cartId = cartIds.get(index).isTextual() ? cartIds.get(index).asText() : null;
            boolean matches = expectedArtifact(context, evidence, ordinals.get(index), AgentArtifactType.CART)
                    .map(reference -> reference.getCartId() != null
                            && reference.getCartId().toString().equals(cartId))
                    .orElse(false);
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesProductKeys(
            AgentToolExecutionContext context,
            List<Integer> ordinals,
            JsonNode arguments,
            String field,
            List<AgentArtifactReference> evidence
    ) {
        JsonNode values = arguments.get(field);
        if (values == null || !values.isArray() || values.size() != ordinals.size()) {
            return false;
        }
        for (int index = 0; index < ordinals.size(); index++) {
            String actual = values.get(index).isTextual() ? values.get(index).asText() : null;
            boolean matches = expectedProduct(context, evidence, ordinals.get(index))
                    .map(reference -> reference.getCanonicalProductKey().equals(actual))
                    .orElse(false);
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSimilarAnchor(
            AgentToolExecutionContext context,
            List<Integer> ordinals,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        if (!single(ordinals)) {
            return false;
        }
        String productKey = text(arguments, "canonicalProductKey");
        if (productKey != null) {
            return expectedProduct(context, evidence, ordinals.getFirst())
                    .map(reference -> reference.getCanonicalProductKey().equals(productKey))
                    .orElse(false);
        }
        String inventoryItemId = text(arguments, "inventoryItemId");
        return expectedInventoryItem(evidence, ordinals.getFirst())
                .map(reference -> reference.getInventoryItemId().toString().equals(inventoryItemId))
                .orElse(false);
    }

    private Optional<AgentArtifactReference> expectedProduct(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        Set<String> turnTokens = descriptiveTokens(context.triggeringUserText());
        AgentArtifactReference selected = null;
        int selectedScore = 0;
        for (List<AgentArtifactReference> productSet : productSets(evidence)) {
            Optional<AgentArtifactReference> candidate = productSet.stream()
                    .filter(reference -> reference.getOrdinal() == ordinal)
                    .findFirst();
            if (candidate.isEmpty()) {
                continue;
            }
            if (turnTokens.isEmpty()) {
                return candidate;
            }
            int score = overlap(turnTokens, descriptiveTokens(candidate.get().getLabel()));
            if (score > selectedScore) {
                selected = candidate.get();
                selectedScore = score;
            }
        }
        return Optional.ofNullable(selected);
    }

    private Optional<AgentCartSnapshotSupport.CartLine> expectedCartLine(
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        List<AgentCartSnapshotSupport.CartSnapshot> cartsWithLines = currentCartSnapshots(evidence).stream()
                .filter(snapshot -> !snapshot.lines().isEmpty())
                .toList();
        if (cartsWithLines.size() != 1) {
            return Optional.empty();
        }
        return atOrdinal(cartsWithLines.getFirst().lines(), ordinal);
    }

    private Optional<AgentArtifactReference> expectedCheckout(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        return expectedArtifact(context, evidence, ordinal, AgentArtifactType.CHECKOUT);
    }

    private Optional<AgentArtifactReference> expectedArtifact(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence,
            int ordinal,
            AgentArtifactType type
    ) {
        if (type == AgentArtifactType.CART) {
            List<AgentArtifactReference> carts = currentCartSnapshots(evidence).stream()
                    .map(AgentCartSnapshotSupport.CartSnapshot::artifact)
                    .toList();
            return atOrdinal(carts, ordinal);
        }
        return atOrdinal(latestArtifactSet(evidence, type), ordinal);
    }

    private boolean matchesLatestCartSet(JsonNode arguments, List<AgentArtifactReference> evidence) {
        Set<String> requested = arrayValues(arguments, "cartIds");
        Set<String> latest = currentCartSnapshots(evidence).stream()
                .map(AgentCartSnapshotSupport.CartSnapshot::cartId)
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return !requested.isEmpty() && requested.equals(latest);
    }

    private boolean matchesSingleLatestCheckout(JsonNode arguments, List<AgentArtifactReference> evidence) {
        List<AgentArtifactReference> latest = latestArtifactSet(evidence, AgentArtifactType.CHECKOUT);
        return latest.size() == 1
                && latest.getFirst().getCartId() != null
                && latest.getFirst().getCartId().toString().equals(text(arguments, "cartId"));
    }

    private List<AgentArtifactReference> latestArtifactSet(
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
                .sorted(java.util.Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .toList();
    }

    private Optional<AgentArtifactReference> expectedInventoryItem(
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        Optional<AgentArtifactReference> newest = evidence.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .findFirst();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        AgentArtifactReference anchor = newest.get();
        List<AgentArtifactReference> items = evidence.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .filter(reference -> sameResultSet(anchor, reference))
                .sorted(java.util.Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .toList();
        return atOrdinal(items, ordinal);
    }

    private <T> Optional<T> atOrdinal(List<T> items, int ordinal) {
        return ordinal > 0 && ordinal <= items.size()
                ? Optional.of(items.get(ordinal - 1))
                : Optional.empty();
    }

    private Optional<AgentArtifactReference> soleLatestProduct(List<AgentArtifactReference> evidence) {
        List<List<AgentArtifactReference>> sets = productSets(evidence);
        return sets.isEmpty() || sets.getFirst().size() != 1
                ? Optional.empty()
                : Optional.of(sets.getFirst().getFirst());
    }

    private List<List<AgentArtifactReference>> productSets(List<AgentArtifactReference> recent) {
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

    private Optional<AgentCartSnapshotSupport.CartSnapshot> currentCart(
            List<AgentArtifactReference> evidence,
            String cartId
    ) {
        if (cartId == null || cartId.isBlank()) {
            return Optional.empty();
        }
        return currentCartSnapshots(evidence).stream()
                .filter(snapshot -> snapshot.cartId().toString().equals(cartId))
                .findFirst();
    }

    private List<AgentCartSnapshotSupport.CartLine> currentCartLines(List<AgentArtifactReference> evidence) {
        return currentCartSnapshots(evidence).stream()
                .flatMap(snapshot -> snapshot.lines().stream())
                .toList();
    }

    private List<AgentCartSnapshotSupport.CartSnapshot> currentCartSnapshots(
            List<AgentArtifactReference> evidence
    ) {
        return cartSnapshotSupport.project(evidence).current();
    }

    private Optional<AgentCartSnapshotSupport.RemovedCartLine> mostRecentlyRemovedLine(
            List<AgentArtifactReference> evidence
    ) {
        return cartSnapshotSupport.project(evidence).mostRecentlyRemovedLine();
    }

    private boolean mustUseCurrentCartForReadd(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence
    ) {
        String turn = Optional.ofNullable(context.triggeringUserText()).orElse("");
        return isReaddReference(turn) && mostRecentlyRemovedLine(evidence).isPresent();
    }

    private boolean isReaddReference(String turn) {
        if (turn == null || turn.isBlank()) {
            return false;
        }
        if (READD_REFERENCE.matcher(turn).find()) {
            return true;
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(turn.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        boolean hasAgainOrBack = false;
        for (int actionIndex = 0; actionIndex < tokens.size(); actionIndex++) {
            String action = tokens.get(actionIndex);
            if (action.equals("again") || action.equals("back")) {
                hasAgainOrBack = true;
            }
            if (!action.equals("add") && !action.equals("put")) {
                continue;
            }
            int markerLimit = Math.min(tokens.size(), actionIndex + 8);
            for (int markerIndex = actionIndex + 1; markerIndex < markerLimit; markerIndex++) {
                String marker = tokens.get(markerIndex);
                if (marker.equals("again") || marker.equals("back")) {
                    return true;
                }
            }
        }
        if (!hasAgainOrBack || !descriptiveTokens(turn).isEmpty()) {
            return false;
        }
        if (CONTEXTUAL_REFERENCE.matcher(turn).find()) {
            return true;
        }
        return tokens.size() == 1
                || (tokens.size() == 2 && tokens.contains("please"));
    }

    private Set<String> selectedOfferKeys(ShoppingMission mission) {
        Set<String> offers = new HashSet<>();
        collectField(objectMapper.readTree(mission.getCoverageJson()), "offerKey", offers);
        return offers;
    }

    private void collectField(JsonNode node, String field, Set<String> values) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
            node.properties().forEach(entry -> collectField(entry.getValue(), field, values));
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collectField(value, field, values));
        }
    }

    private Set<String> stringValues(String json) {
        JsonNode node = objectMapper.readTree(json);
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        });
        return Set.copyOf(values);
    }

    private Set<String> arrayValues(JsonNode arguments, String field) {
        JsonNode values = arguments == null ? null : arguments.get(field);
        if (values == null || !values.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            }
        });
        return Set.copyOf(result);
    }

    private Set<String> arrayField(JsonNode arguments, String arrayField, String itemField) {
        JsonNode values = arguments == null ? null : arguments.get(arrayField);
        if (values == null || !values.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            String item = text(value, itemField);
            if (item != null && !item.isBlank()) {
                result.add(item);
            }
        });
        return Set.copyOf(result);
    }

    private boolean allLiteral(String turn, Set<String> references) {
        return !references.isEmpty() && references.stream().allMatch(reference -> literalReference(turn, reference));
    }

    private boolean literalReference(String turn, String reference) {
        return turn != null && reference != null && !reference.isBlank()
                && turn.toLowerCase(Locale.ROOT).contains(reference.toLowerCase(Locale.ROOT));
    }

    private boolean offerBelongsToProduct(UUID conversationId, String offerKey, String canonicalProductKey) {
        if (offerKey == null || canonicalProductKey == null) {
            return false;
        }
        return artifactRepository.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                        conversationId, offerKey)
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.OFFER
                        || reference.getArtifactType() == AgentArtifactType.PRODUCT
                        || reference.getArtifactType() == AgentArtifactType.SAVED_PRODUCT)
                .filter(reference -> offerKey.equals(reference.getOfferKey()))
                .filter(reference -> canonicalProductKey.equals(reference.getCanonicalProductKey()))
                .isPresent();
    }

    private List<AgentArtifactReference> recent(UUID conversationId) {
        return artifactRepository.findByConversationIdOrderByCreatedAtDescOrdinalAsc(
                conversationId, PageRequest.of(0, REFERENCE_WINDOW));
    }

    private boolean isProductReference(AgentArtifactReference reference) {
        return (reference.getArtifactType() == AgentArtifactType.PRODUCT
                || reference.getArtifactType() == AgentArtifactType.SAVED_PRODUCT)
                && reference.getCanonicalProductKey() != null;
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

    private List<Integer> ordinals(String userText) {
        if (userText == null) {
            return List.of();
        }
        Matcher numericSelection = NUMERIC_ORDINAL_SELECTION.matcher(userText);
        if (numericSelection.matches()) {
            return List.of(Integer.parseInt(numericSelection.group(1)));
        }
        Matcher firstCount = FIRST_COUNT.matcher(userText);
        if (firstCount.find()) {
            int count = switch (firstCount.group(1).toLowerCase(Locale.ROOT)) {
                case "two", "2" -> 2;
                case "three", "3" -> 3;
                case "four", "4" -> 4;
                default -> throw new IllegalStateException("Unsupported reference count");
            };
            return java.util.stream.IntStream.rangeClosed(1, count).boxed().toList();
        }
        Matcher matcher = ORDINAL.matcher(userText);
        java.util.LinkedHashSet<Integer> values = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            values.add(ordinalValue(matcher.group(1)));
        }
        return List.copyOf(values);
    }

    private int ordinalValue(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "first", "1st" -> 1;
            case "second", "2nd" -> 2;
            case "third", "3rd" -> 3;
            case "fourth", "4th" -> 4;
            case "fifth", "5th" -> 5;
            case "sixth", "6th" -> 6;
            case "seventh", "7th" -> 7;
            case "eighth", "8th" -> 8;
            case "ninth", "9th" -> 9;
            case "tenth", "10th" -> 10;
            default -> throw new IllegalStateException("Unsupported ordinal");
        };
    }

    private boolean single(List<Integer> ordinals) {
        return ordinals.size() == 1;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

}
