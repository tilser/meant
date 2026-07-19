package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
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
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> NON_DESCRIPTIVE_TOKENS = Set.of(
            "add", "and", "bag", "basket", "both", "buy", "cart", "check", "checkout", "delete",
            "details", "from", "get", "into", "item", "items", "more", "need", "new", "one", "ones",
            "order", "pair", "pin", "place", "please", "product", "products", "purchase", "put", "remove",
            "same", "save", "show", "size", "take", "tell", "that", "the", "them", "these", "this", "those",
            "want", "watch", "with"
    );

    private final AgentArtifactReferenceRepository artifactRepository;
    private final ObjectMapper objectMapper;

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
        return matchesOrdinals(context, toolName, canonicalArgumentsJson, ordinals);
    }

    /**
     * Requires a model-driven commerce mutation to bind its arguments to either an explicit result ordinal or an
     * exact server-issued reference written in the current user turn. Direct CTA actions bypass this policy because
     * their target is supplied by the clicked component rather than selected by the model.
     */
    public boolean matchesMutationTarget(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson
    ) {
        if (context == null || context.runId() == null) {
            return true;
        }
        List<Integer> ordinals = ordinals(context.triggeringUserText());
        if (!ordinals.isEmpty()) {
            return matchesOrdinals(context, toolName, canonicalArgumentsJson, ordinals);
        }
        return matchesLiteralReference(context, toolName, canonicalArgumentsJson);
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
            List<Integer> ordinals
    ) {
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            return switch (toolName) {
                case "pin_product", "unpin_product", "watch_product", "unwatch_product" ->
                        single(ordinals) && expectedProduct(context, ordinals.getFirst())
                                .map(expected -> expected.getCanonicalProductKey()
                                        .equals(text(arguments, "canonicalProductKey")))
                                .orElse(false);
                case "prepare_carts" -> matchesPreparedOffers(context, ordinals, arguments);
                case "add_cart_line" -> single(ordinals) && expectedProduct(context, ordinals.getFirst())
                        .map(expected -> offerBelongsToProduct(
                                context.conversationId(),
                                text(arguments, "offerKey"),
                                expected.getCanonicalProductKey()
                        ))
                        .orElse(false)
                        && literalReference(context.triggeringUserText(), text(arguments, "cartId"));
                case "update_cart_line", "remove_cart_line" -> single(ordinals)
                        && expectedCartLine(context, ordinals.getFirst())
                        .map(expected -> expected.getCartLineId().toString().equals(text(arguments, "cartLineId"))
                                && expected.getCartId().toString().equals(text(arguments, "cartId")))
                        .orElse(false);
                case "prepare_checkout" -> matchesCartIds(context, ordinals, arguments);
                case "update_checkout" -> single(ordinals)
                        && expectedCheckout(context, ordinals.getFirst())
                        .map(expected -> expected.getCartId().toString().equals(text(arguments, "cartId")))
                        .orElse(false);
                case "compare_products" -> matchesProductKeys(context, ordinals, arguments, "canonicalProductKeys");
                case "pick_recommended_product" ->
                        matchesProductKeys(context, ordinals, arguments, "canonicalProductKeys");
                case "get_product", "get_product_reviews", "find_discount_codes" ->
                        single(ordinals) && expectedProduct(context, ordinals.getFirst())
                                .map(expected -> expected.getCanonicalProductKey()
                                        .equals(text(arguments, "canonicalProductKey")))
                                .orElse(false);
                case "find_similar_products" -> matchesSimilarAnchor(context, ordinals, arguments);
                default -> true;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesLiteralReference(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson
    ) {
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            String turn = context.triggeringUserText();
            return switch (toolName) {
                case "pin_product", "unpin_product", "watch_product", "unwatch_product" ->
                        literalReference(turn, text(arguments, "canonicalProductKey"))
                                || matchesNamedProduct(context, text(arguments, "canonicalProductKey"));
                case "prepare_carts" -> allLiteral(turn, arrayField(arguments, "offers", "offerKey"))
                        || matchesNamedPreparedOffer(context, arguments);
                case "add_cart_line" -> literalReference(turn, text(arguments, "offerKey"))
                        && literalReference(turn, text(arguments, "cartId"));
                case "update_cart_line", "remove_cart_line" ->
                        literalReference(turn, text(arguments, "cartLineId"))
                                && cartLineMatches(arguments, context.conversationId());
                case "prepare_checkout" -> matchesLatestCartSet(context, arguments);
                case "update_checkout" -> matchesSingleLatestCheckout(context, arguments);
                default -> true;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesNamedProduct(AgentToolExecutionContext context, String canonicalProductKey) {
        return uniquelyMentionedProduct(context)
                .map(reference -> reference.getCanonicalProductKey().equals(canonicalProductKey))
                .orElse(false);
    }

    private boolean matchesNamedPreparedOffer(AgentToolExecutionContext context, JsonNode arguments) {
        JsonNode offers = arguments == null ? null : arguments.get("offers");
        if (offers == null || !offers.isArray() || offers.size() != 1) {
            return false;
        }
        String offerKey = text(offers.get(0), "offerKey");
        if (offerKey == null || offerKey.isBlank()) {
            return false;
        }
        return uniquelyMentionedProduct(context)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false);
    }

    /** Resolves descriptive follow-ups only when one product in the latest result set is the unique best match. */
    private Optional<AgentArtifactReference> uniquelyMentionedProduct(AgentToolExecutionContext context) {
        Set<String> turnTokens = descriptiveTokens(context.triggeringUserText());
        if (turnTokens.isEmpty()) {
            return Optional.empty();
        }
        List<AgentArtifactReference> recent = recent(context.conversationId());
        Optional<AgentArtifactReference> newest = recent.stream()
                .filter(this::isProductReference)
                .findFirst();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        AgentArtifactReference anchor = newest.get();
        List<AgentArtifactReference> candidates = recent.stream()
                .filter(this::isProductReference)
                .filter(reference -> sameResultSet(anchor, reference))
                .filter(reference -> reference.getLabel() != null && !reference.getLabel().isBlank())
                .toList();
        int bestScore = candidates.stream()
                .mapToInt(reference -> overlap(turnTokens, descriptiveTokens(reference.getLabel())))
                .max()
                .orElse(0);
        if (bestScore == 0) {
            return Optional.empty();
        }
        List<AgentArtifactReference> matches = candidates.stream()
                .filter(reference -> overlap(turnTokens, descriptiveTokens(reference.getLabel())) == bestScore)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private Set<String> descriptiveTokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 3 && !NON_DESCRIPTIVE_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return Set.copyOf(tokens);
    }

    private int overlap(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }

    private boolean matchesPreparedOffers(
            AgentToolExecutionContext context,
            List<Integer> ordinals,
            JsonNode arguments
    ) {
        JsonNode offers = arguments.get("offers");
        if (offers == null || !offers.isArray() || offers.size() != ordinals.size()) {
            return false;
        }
        for (int index = 0; index < ordinals.size(); index++) {
            String offerKey = text(offers.get(index), "offerKey");
            boolean matches = expectedProduct(context, ordinals.get(index))
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
            JsonNode arguments
    ) {
        JsonNode cartIds = arguments.get("cartIds");
        if (cartIds == null || !cartIds.isArray() || cartIds.size() != ordinals.size()) {
            return false;
        }
        for (int index = 0; index < ordinals.size(); index++) {
            String cartId = cartIds.get(index).isTextual() ? cartIds.get(index).asText() : null;
            boolean matches = expectedArtifact(context, ordinals.get(index), AgentArtifactType.CART)
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
            String field
    ) {
        JsonNode values = arguments.get(field);
        if (values == null || !values.isArray() || values.size() != ordinals.size()) {
            return false;
        }
        for (int index = 0; index < ordinals.size(); index++) {
            String actual = values.get(index).isTextual() ? values.get(index).asText() : null;
            boolean matches = expectedProduct(context, ordinals.get(index))
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
            JsonNode arguments
    ) {
        if (!single(ordinals)) {
            return false;
        }
        String productKey = text(arguments, "canonicalProductKey");
        if (productKey != null) {
            return expectedProduct(context, ordinals.getFirst())
                    .map(reference -> reference.getCanonicalProductKey().equals(productKey))
                    .orElse(false);
        }
        String inventoryItemId = text(arguments, "inventoryItemId");
        return expectedInventoryItem(context, ordinals.getFirst())
                .map(reference -> reference.getInventoryItemId().toString().equals(inventoryItemId))
                .orElse(false);
    }

    private Optional<AgentArtifactReference> expectedProduct(AgentToolExecutionContext context, int ordinal) {
        List<AgentArtifactReference> recent = recent(context.conversationId());
        Optional<AgentArtifactReference> newest = recent.stream()
                .filter(this::isProductReference)
                .findFirst();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        AgentArtifactReference anchor = newest.get();
        return recent.stream()
                .filter(this::isProductReference)
                .filter(reference -> sameResultSet(anchor, reference))
                .filter(reference -> reference.getOrdinal() == ordinal)
                .findFirst();
    }

    private Optional<AgentArtifactReference> expectedCartLine(AgentToolExecutionContext context, int ordinal) {
        return expectedArtifact(context, ordinal, AgentArtifactType.CART_LINE);
    }

    private Optional<AgentArtifactReference> expectedCheckout(AgentToolExecutionContext context, int ordinal) {
        return expectedArtifact(context, ordinal, AgentArtifactType.CHECKOUT);
    }

    private Optional<AgentArtifactReference> expectedArtifact(
            AgentToolExecutionContext context,
            int ordinal,
            AgentArtifactType type
    ) {
        List<AgentArtifactReference> compatible = latestArtifactSet(context, type);
        return ordinal <= compatible.size() ? Optional.of(compatible.get(ordinal - 1)) : Optional.empty();
    }

    private boolean matchesLatestCartSet(AgentToolExecutionContext context, JsonNode arguments) {
        Set<String> requested = arrayValues(arguments, "cartIds");
        Set<String> latest = latestArtifactSet(context, AgentArtifactType.CART).stream()
                .map(AgentArtifactReference::getCartId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return !requested.isEmpty() && requested.equals(latest);
    }

    private boolean matchesSingleLatestCheckout(AgentToolExecutionContext context, JsonNode arguments) {
        List<AgentArtifactReference> latest = latestArtifactSet(context, AgentArtifactType.CHECKOUT);
        return latest.size() == 1
                && latest.getFirst().getCartId() != null
                && latest.getFirst().getCartId().toString().equals(text(arguments, "cartId"));
    }

    private List<AgentArtifactReference> latestArtifactSet(
            AgentToolExecutionContext context,
            AgentArtifactType type
    ) {
        List<AgentArtifactReference> recent = recent(context.conversationId());
        Optional<AgentArtifactReference> newest = recent.stream()
                .filter(reference -> reference.getArtifactType() == type)
                .findFirst();
        if (newest.isEmpty()) {
            return List.of();
        }
        AgentArtifactReference anchor = newest.get();
        return recent.stream()
                .filter(reference -> reference.getArtifactType() == type)
                .filter(reference -> sameResultSet(anchor, reference))
                .sorted(java.util.Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .toList();
    }

    private Optional<AgentArtifactReference> expectedInventoryItem(
            AgentToolExecutionContext context,
            int ordinal
    ) {
        List<AgentArtifactReference> recent = recent(context.conversationId());
        Optional<AgentArtifactReference> newest = recent.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .findFirst();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        AgentArtifactReference anchor = newest.get();
        List<AgentArtifactReference> items = recent.stream()
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .filter(reference -> sameResultSet(anchor, reference))
                .sorted(java.util.Comparator.comparingInt(AgentArtifactReference::getOrdinal))
                .toList();
        return ordinal <= items.size() ? Optional.of(items.get(ordinal - 1)) : Optional.empty();
    }

    private boolean cartLineMatches(JsonNode arguments, UUID conversationId) {
        String cartLineId = text(arguments, "cartLineId");
        String cartId = text(arguments, "cartId");
        if (cartLineId == null || cartId == null) {
            return false;
        }
        return artifactRepository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                        conversationId, "cart-line:" + cartLineId)
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.CART_LINE)
                .filter(reference -> reference.getCartLineId() != null
                        && reference.getCartLineId().toString().equals(cartLineId))
                .filter(reference -> reference.getCartId() != null
                        && reference.getCartId().toString().equals(cartId))
                .isPresent();
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
