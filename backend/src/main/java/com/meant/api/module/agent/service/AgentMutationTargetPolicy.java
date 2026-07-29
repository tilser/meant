package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.atOrdinal;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.currentCart;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.currentCartLines;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.currentCartSnapshots;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.latestArtifactSet;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.matchesLatestCartSet;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.matchesResolvedInventoryReadTarget;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.matchesResolvedProductReadTarget;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.matchesSingleLatestCheckout;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.mostRecentlyRemovedLine;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.productSets;
import static com.meant.api.module.agent.service.AgentArtifactEvidenceSupport.soleLatestProduct;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.allLiteral;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.clarificationOrdinals;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.descriptiveTokens;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.hasContextualReference;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.hasRankedProductReference;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.isReaddReference;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.literalReference;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.ordinals;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.overlap;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.pendingProductClarificationAttempt;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.pendingProductSelectionAttempt;
import static com.meant.api.module.agent.service.AgentMutationTargetTextSupport.productDetailAnchorMatches;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.arrayField;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.text;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentProductClarificationEvaluation;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AgentMutationTargetPolicy {

    private static final int REFERENCE_WINDOW = 200;
    private static final Set<String> PRODUCT_SELECTION_TOOLS = Set.of(
            "pin_product", "unpin_product", "watch_product", "unwatch_product",
            "prepare_carts", "add_cart_line",
            "get_product", "get_product_reviews", "find_discount_codes", "find_similar_products"
    );
    private static final Set<String> SINGLE_PRODUCT_SELECTION_TOOLS = Set.of(
            "pin_product", "unpin_product", "watch_product", "unwatch_product", "add_cart_line",
            "get_product", "get_product_reviews", "find_discount_codes", "find_similar_products"
    );
    private static final Set<String> SINGLE_PRODUCT_READ_TOOLS = Set.of(
            "get_product", "get_product_reviews", "find_discount_codes"
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
        if (pendingClarificationFor(context, toolName)) {
            try {
                return matchesPendingProductTarget(
                        context,
                        toolName,
                        canonicalArgumentsJson,
                        recent(context.conversationId())
                );
            } catch (RuntimeException exception) {
                return false;
            }
        }
        try {
            List<AgentArtifactReference> evidence = recent(context.conversationId());
            List<Integer> ordinals = ordinals(context.triggeringUserText());
            if (!ordinals.isEmpty()) {
                return matchesOrdinals(context, toolName, canonicalArgumentsJson, ordinals, evidence);
            }
            if (toolName.equals("get_inventory_item")) {
                return matchesResolvedInventoryReadTarget(
                        context,
                        canonicalArgumentsJson,
                        evidence,
                        objectMapper
                );
            }
            return !SINGLE_PRODUCT_READ_TOOLS.contains(toolName)
                    || matchesResolvedProductReadTarget(
                            context,
                            canonicalArgumentsJson,
                            productClarificationCandidates(context, evidence),
                            objectMapper
                    );
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
            List<AgentArtifactReference> evidence = recent(context.conversationId());
            if (pendingClarificationFor(context, toolName)) {
                return matchesPendingProductTarget(
                        context,
                        toolName,
                        canonicalArgumentsJson,
                        evidence
                );
            }
            List<Integer> ordinals = ordinals(context.triggeringUserText());
            if (!ordinals.isEmpty()) {
                return matchesOrdinals(context, toolName, canonicalArgumentsJson, ordinals, evidence);
            }
            return matchesLiteralReference(context, toolName, canonicalArgumentsJson, evidence);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Reports ambiguity in the user's product target independently of the stable IDs proposed by the model.
     * This lets the coordinator ask the user only when their instruction is unresolved, while a wrong model
     * argument remains an ordinary rejected invocation that the model can correct itself.
     */
    public boolean requiresProductClarification(AgentToolExecutionContext context, String toolName) {
        return evaluateProductClarification(context, toolName).clarificationRequired();
    }

    /**
     * Evaluates ambiguity and returns the exact candidates from the same artifact snapshot.
     */
    public AgentProductClarificationEvaluation evaluateProductClarification(
            AgentToolExecutionContext context,
            String toolName
    ) {
        if (toolName == null) {
            return noClarification();
        }
        return evaluateProductClarifications(context, List.of(toolName))
                .getOrDefault(toolName, noClarification());
    }

    /**
     * Evaluates multiple possible actions against one recent-artifact snapshot.
     */
    public Map<String, AgentProductClarificationEvaluation> evaluateProductClarifications(
            AgentToolExecutionContext context,
            List<String> toolNames
    ) {
        if (context == null || toolNames == null || toolNames.isEmpty()) {
            return Map.of();
        }
        List<String> applicableTools = toolNames.stream()
                .filter(Objects::nonNull)
                .filter(PRODUCT_SELECTION_TOOLS::contains)
                .distinct()
                .toList();
        if (applicableTools.isEmpty()) {
            return Map.of();
        }

        boolean needsEvidence = context.runId() != null
                && context.conversationId() != null
                && applicableTools.stream().anyMatch(toolName -> !pendingClarificationFor(context, toolName));
        List<AgentArtifactReference> evidence = List.of();
        boolean evidenceAvailable = !needsEvidence;
        if (needsEvidence) {
            try {
                evidence = recent(context.conversationId());
                evidenceAvailable = true;
            } catch (RuntimeException exception) {
                evidenceAvailable = false;
            }
        }

        Map<String, AgentProductClarificationEvaluation> evaluations = new LinkedHashMap<>();
        for (String toolName : applicableTools) {
            try {
                evaluations.put(toolName, evaluateProductClarification(
                        context,
                        toolName,
                        evidence,
                        evidenceAvailable
                ));
            } catch (RuntimeException exception) {
                evaluations.put(toolName, noClarification());
            }
        }
        return Map.copyOf(evaluations);
    }

    private AgentProductClarificationEvaluation evaluateProductClarification(
            AgentToolExecutionContext context,
            String toolName,
            List<AgentArtifactReference> evidence,
            boolean evidenceAvailable
    ) {
        if (context == null
                || context.runId() == null
                || !PRODUCT_SELECTION_TOOLS.contains(toolName)) {
            return noClarification();
        }
        if (pendingClarificationFor(context, toolName)) {
            List<AgentVisibleProductReference> candidates =
                    List.copyOf(context.pendingProductClarification().products());
            boolean required = pendingProductClarificationAttempt(context, toolName)
                    && pendingProductSelection(context).isEmpty();
            return new AgentProductClarificationEvaluation(required, candidates);
        }
        if (!evidenceAvailable) {
            return noClarification();
        }

        List<AgentVisibleProductReference> candidates = productClarificationCandidates(context, evidence);
        if (candidates.isEmpty() || mustUseCurrentCartForReadd(context, evidence)) {
            return new AgentProductClarificationEvaluation(false, candidates);
        }
        Optional<AgentArtifactReference> rankedCurrentRunProduct =
                currentRunRankedProduct(context, evidence);
        if (hasRankedProductReference(context.triggeringUserText())
                && rankedCurrentRunProduct.isPresent()) {
            return new AgentProductClarificationEvaluation(
                    false,
                    visibleReferences(List.of(rankedCurrentRunProduct.get()))
            );
        }

        List<Integer> requestedOrdinals = ordinals(context.triggeringUserText());
        if (!requestedOrdinals.isEmpty()) {
            boolean required = SINGLE_PRODUCT_SELECTION_TOOLS.contains(toolName) && !single(requestedOrdinals)
                    || requestedOrdinals.stream()
                    .anyMatch(ordinal -> expectedProductKey(context, evidence, ordinal).isEmpty());
            return new AgentProductClarificationEvaluation(required, candidates);
        }

        String userText = Optional.ofNullable(context.triggeringUserText()).orElse("");
        long stableMatches = candidates.stream()
                .filter(product -> literalReference(userText, product.canonicalProductKey())
                        || literalReference(userText, product.recommendedOfferKey()))
                .count();
        if (stableMatches > 0) {
            return new AgentProductClarificationEvaluation(stableMatches != 1, candidates);
        }

        long anchoredMatches = candidates.stream()
                .filter(product -> productDetailAnchorMatches(userText, product.title()))
                .count();
        if (anchoredMatches > 0) {
            return new AgentProductClarificationEvaluation(anchoredMatches != 1, candidates);
        }

        Set<String> description = descriptiveTokens(userText);
        if (!description.isEmpty()) {
            long namedMatches = candidates.stream()
                    .filter(product -> descriptiveTokens(product.title()).containsAll(description))
                    .count();
            return new AgentProductClarificationEvaluation(namedMatches != 1, candidates);
        }
        return new AgentProductClarificationEvaluation(candidates.size() > 1, candidates);
    }

    private AgentProductClarificationEvaluation noClarification() {
        return new AgentProductClarificationEvaluation(false, List.of());
    }

    /** Returns clarification choices in the same precedence order used to bind product ordinals. */
    public List<AgentVisibleProductReference> productClarificationCandidates(
            AgentToolExecutionContext context,
            String toolName
    ) {
        if (context == null || context.conversationId() == null) {
            return List.of();
        }
        if (pendingClarificationFor(context, toolName)) {
            return List.copyOf(context.pendingProductClarification().products());
        }
        try {
            return productClarificationCandidates(context, recent(context.conversationId()));
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** Resolves one explicit ordinal using the same trusted precedence and title checks as tool authorization. */
    public Optional<AgentVisibleProductReference> explicitProductTarget(AgentToolExecutionContext context) {
        if (context == null || context.conversationId() == null || context.runId() == null) {
            return Optional.empty();
        }
        try {
            List<AgentArtifactReference> evidence = recent(context.conversationId());
            List<Integer> requestedOrdinals = ordinals(context.triggeringUserText());
            if (!single(requestedOrdinals)) {
                return Optional.empty();
            }
            String expectedProductKey = expectedProductKey(
                    context,
                    evidence,
                    requestedOrdinals.getFirst()
            ).orElse(null);
            if (expectedProductKey == null) {
                return Optional.empty();
            }
            return productClarificationCandidates(context, evidence).stream()
                    .filter(product -> expectedProductKey.equals(product.canonicalProductKey()))
                    .findFirst();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** True only when the current turn is trying to answer the recorded product-choice question. */
    public boolean isPendingProductSelectionAnswer(
            AgentToolExecutionContext context,
            String toolName
    ) {
        return context != null
                && pendingClarificationFor(context, toolName)
                && pendingProductSelectionAttempt(context);
    }

    private List<AgentVisibleProductReference> productClarificationCandidates(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence
    ) {
        List<AgentArtifactReference> currentRun = evidence.stream()
                .filter(reference -> Objects.equals(reference.getRunId(), context.runId()))
                .toList();
        List<List<AgentArtifactReference>> currentRunSets = productSets(currentRun);
        if (!currentRunSets.isEmpty()) {
            return visibleReferences(currentRunSets.getFirst());
        }
        if (context.visibleProductContext() != null
                && !context.visibleProductContext().products().isEmpty()) {
            return List.copyOf(context.visibleProductContext().products());
        }
        List<List<AgentArtifactReference>> priorSets = productSets(evidence);
        return priorSets.isEmpty() ? List.of() : visibleReferences(priorSets.getFirst());
    }

    private List<AgentVisibleProductReference> visibleReferences(List<AgentArtifactReference> references) {
        return references.stream()
                .map(reference -> new AgentVisibleProductReference(
                        reference.getOrdinal(),
                        reference.getOrdinal(),
                        reference.getCanonicalProductKey(),
                        reference.getOfferKey(),
                        reference.getLabel()
                ))
                .toList();
    }

    /** Limits broad READY-mission delegation to the selections and carts persisted on that owned mission. */
    public boolean matchesDelegatedMission(
            ShoppingMission mission,
            String toolName,
            String canonicalArgumentsJson
    ) {
        return AgentMissionTargetSupport.matchesDelegatedMission(
                mission,
                toolName,
                canonicalArgumentsJson,
                objectMapper
        );
    }

    /** Binds mission updates and coverage evaluation to the latest active mission selected by the policy. */
    public boolean matchesMissionTarget(ShoppingMission mission, String canonicalArgumentsJson) {
        return AgentMissionTargetSupport.matchesMissionTarget(
                mission,
                canonicalArgumentsJson,
                objectMapper
        );
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
                        single(ordinals) && expectedProductKey(context, evidence, ordinals.getFirst())
                                .map(expected -> expected.equals(text(arguments, "canonicalProductKey")))
                                .orElse(false);
                case "prepare_carts" -> !mustUseCurrentCartForReadd(context, evidence)
                        && matchesPreparedOffers(context, ordinals, arguments, evidence);
                case "add_cart_line" -> single(ordinals)
                        && expectedProductKey(context, evidence, ordinals.getFirst())
                        .map(expected -> offerBelongsToProduct(
                                context.conversationId(),
                                text(arguments, "offerKey"),
                                expected
                        ))
                        .orElse(false)
                        && currentCart(evidence, text(arguments, "cartId"), cartSnapshotSupport).isPresent();
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
                        single(ordinals) && expectedProductKey(context, evidence, ordinals.getFirst())
                                .map(expected -> expected.equals(text(arguments, "canonicalProductKey")))
                                .orElse(false);
                case "get_inventory_item" ->
                        single(ordinals) && expectedInventoryItem(evidence, ordinals.getFirst())
                                .map(expected -> expected.getInventoryItemId().toString()
                                        .equals(text(arguments, "inventoryItemId")))
                                .orElse(false);
                case "find_similar_products" -> matchesSimilarAnchor(context, ordinals, arguments, evidence);
                default -> true;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesPendingProductTarget(
            AgentToolExecutionContext context,
            String toolName,
            String canonicalArgumentsJson,
            List<AgentArtifactReference> evidence
    ) {
        AgentVisibleProductReference selected = pendingProductSelection(context).orElse(null);
        if (selected == null) {
            return false;
        }
        JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
        return switch (toolName) {
            case "pin_product", "unpin_product", "watch_product", "unwatch_product",
                    "get_product", "get_product_reviews", "find_discount_codes" ->
                    selected.canonicalProductKey().equals(text(arguments, "canonicalProductKey"));
            case "prepare_carts" -> !mustUseCurrentCartForReadd(context, evidence)
                    && singlePreparedOfferMatches(context, arguments, selected);
            case "add_cart_line" -> selectedOfferMatches(
                    context,
                    text(arguments, "offerKey"),
                    selected
            ) && currentCart(evidence, text(arguments, "cartId"), cartSnapshotSupport).isPresent();
            case "compare_products", "pick_recommended_product" ->
                    singleProductKeyMatches(arguments, "canonicalProductKeys", selected);
            case "find_similar_products" ->
                    selected.canonicalProductKey().equals(text(arguments, "canonicalProductKey"));
            default -> false;
        };
    }

    private Optional<AgentVisibleProductReference> pendingProductSelection(
            AgentToolExecutionContext context
    ) {
        List<AgentVisibleProductReference> products = context.pendingProductClarification().products();
        String userText = Optional.ofNullable(context.triggeringUserText()).orElse("");
        List<Integer> selections = clarificationOrdinals(userText);
        if (!selections.isEmpty()) {
            if (!single(selections)) {
                return Optional.empty();
            }
            Set<String> description = descriptiveTokens(userText);
            return products.stream()
                    .filter(product -> product.visibleOrdinal() == selections.getFirst())
                    .filter(product -> description.isEmpty()
                            || descriptiveTokens(product.title()).containsAll(description))
                    .findFirst();
        }

        List<AgentVisibleProductReference> stableReferenceMatches = products.stream()
                .filter(product -> literalReference(userText, product.canonicalProductKey())
                        || literalReference(userText, product.recommendedOfferKey()))
                .toList();
        if (stableReferenceMatches.size() == 1) {
            return Optional.of(stableReferenceMatches.getFirst());
        }

        Set<String> description = descriptiveTokens(userText);
        if (description.isEmpty()) {
            return Optional.empty();
        }
        List<AgentVisibleProductReference> named = products.stream()
                .filter(product -> descriptiveTokens(product.title()).containsAll(description))
                .toList();
        return named.size() == 1 ? Optional.of(named.getFirst()) : Optional.empty();
    }

    private boolean singlePreparedOfferMatches(
            AgentToolExecutionContext context,
            JsonNode arguments,
            AgentVisibleProductReference selected
    ) {
        JsonNode offers = arguments == null ? null : arguments.get("offers");
        return offers != null
                && offers.isArray()
                && offers.size() == 1
                && selectedOfferMatches(context, text(offers.get(0), "offerKey"), selected);
    }

    private boolean singleProductKeyMatches(
            JsonNode arguments,
            String field,
            AgentVisibleProductReference selected
    ) {
        JsonNode keys = arguments == null ? null : arguments.get(field);
        return keys != null
                && keys.isArray()
                && keys.size() == 1
                && keys.get(0).isTextual()
                && selected.canonicalProductKey().equals(keys.get(0).asText());
    }

    private boolean selectedOfferMatches(
            AgentToolExecutionContext context,
            String offerKey,
            AgentVisibleProductReference selected
    ) {
        if (offerKey == null || offerKey.isBlank()) {
            return false;
        }
        if (selected.recommendedOfferKey() != null
                && !selected.recommendedOfferKey().isBlank()
                && !selected.recommendedOfferKey().equals(offerKey)) {
            return false;
        }
        return offerBelongsToProduct(
                context.conversationId(),
                offerKey,
                selected.canonicalProductKey()
        );
    }

    private boolean pendingClarificationFor(AgentToolExecutionContext context, String toolName) {
        return context.pendingProductClarification() != null
                && context.pendingProductClarification().continuesWith(toolName)
                && !context.pendingProductClarification().products().isEmpty();
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
                        || matchesContextualPreparedOffer(context, arguments, evidence)
                        || matchesCurrentRunRankedPreparedOffer(context, arguments, evidence));
                case "add_cart_line" -> matchesContextualCartAddition(context, arguments, evidence)
                        || matchesCurrentRunRankedCartAddition(context, arguments, evidence);
                case "update_cart_line", "remove_cart_line" ->
                        matchesContextualCartLine(context, arguments, evidence);
                case "prepare_checkout" -> matchesLatestCartSet(arguments, evidence, cartSnapshotSupport);
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
                || !hasContextualReference(turn)) {
            return false;
        }
        String offerKey = text(offers.get(0), "offerKey");
        return soleLatestProduct(evidence)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false);
    }

    private boolean matchesCurrentRunRankedPreparedOffer(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        if (!hasRankedProductReference(context.triggeringUserText())) {
            return false;
        }
        JsonNode offers = arguments == null ? null : arguments.get("offers");
        if (offers == null || !offers.isArray() || offers.size() != 1) {
            return false;
        }
        String offerKey = text(offers.get(0), "offerKey");
        return currentRunRankedProduct(context, evidence)
                .filter(reference -> offerBelongsToProduct(
                        reference,
                        offerKey,
                        reference.getCanonicalProductKey()
                ))
                .isPresent();
    }

    private Optional<AgentArtifactReference> currentRunRankedProduct(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence
    ) {
        List<AgentArtifactReference> currentRunEvidence = evidence.stream()
                .filter(reference -> Objects.equals(reference.getRunId(), context.runId()))
                .toList();
        List<List<AgentArtifactReference>> currentRunSets = productSets(currentRunEvidence);
        return currentRunSets.isEmpty() || currentRunSets.getFirst().isEmpty()
                ? Optional.empty()
                : Optional.of(currentRunSets.getFirst().getFirst());
    }

    private boolean matchesContextualCartAddition(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        String cartId = text(arguments, "cartId");
        String offerKey = text(arguments, "offerKey");
        AgentCartSnapshotSupport.CartState cartState = cartSnapshotSupport.project(evidence);
        boolean currentCartExists = cartId != null && !cartId.isBlank()
                && cartState.current().stream()
                .anyMatch(snapshot -> snapshot.cartId().toString().equals(cartId));
        if (!currentCartExists || offerKey == null || offerKey.isBlank()) {
            return false;
        }
        String turn = Optional.ofNullable(context.triggeringUserText()).orElse("");
        if (isReaddReference(turn)) {
            return cartState.mostRecentlyRemovedLine()
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
        if (hasContextualReference(turn)
                && soleLatestProduct(evidence)
                .map(reference -> offerBelongsToProduct(
                        context.conversationId(), offerKey, reference.getCanonicalProductKey()))
                .orElse(false)) {
            return true;
        }
        return false;
    }

    private boolean matchesCurrentRunRankedCartAddition(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        if (!hasRankedProductReference(context.triggeringUserText())) {
            return false;
        }
        String cartId = text(arguments, "cartId");
        String offerKey = text(arguments, "offerKey");
        if (currentCart(evidence, cartId, cartSnapshotSupport).isEmpty()) {
            return false;
        }
        return currentRunRankedProduct(context, evidence)
                .filter(reference -> offerBelongsToProduct(
                        reference,
                        offerKey,
                        reference.getCanonicalProductKey()
                ))
                .isPresent();
    }

    private boolean matchesContextualCartLine(
            AgentToolExecutionContext context,
            JsonNode arguments,
            List<AgentArtifactReference> evidence
    ) {
        String cartId = text(arguments, "cartId");
        String cartLineId = text(arguments, "cartLineId");
        List<AgentCartSnapshotSupport.CartLine> currentLines = currentCartLines(evidence, cartSnapshotSupport);
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
                && hasContextualReference(context.triggeringUserText())
                && currentLines.size() == 1
                && currentLines.getFirst().equals(proposed.get());
    }

    /** Resolves descriptive follow-ups to one exact label match in the newest compatible result set. */
    private Optional<AgentArtifactReference> uniquelyMentionedProduct(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence
    ) {
        String userText = context.triggeringUserText();
        Set<String> turnTokens = descriptiveTokens(context.triggeringUserText());
        if (turnTokens.isEmpty() && (userText == null || userText.isBlank())) {
            return Optional.empty();
        }
        for (List<AgentArtifactReference> productSet : productSets(evidence)) {
            List<AgentArtifactReference> anchored = productSet.stream()
                    .filter(reference -> productDetailAnchorMatches(userText, reference.getLabel()))
                    .toList();
            if (!anchored.isEmpty()) {
                return anchored.size() == 1 ? Optional.of(anchored.getFirst()) : Optional.empty();
            }
            if (turnTokens.isEmpty()) {
                continue;
            }
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
        List<String> offerKeys = new ArrayList<>(ordinals.size());
        List<String> expectedProductKeys = new ArrayList<>(ordinals.size());
        for (int index = 0; index < ordinals.size(); index++) {
            String offerKey = text(offers.get(index), "offerKey");
            String expectedProductKey = expectedProductKey(context, evidence, ordinals.get(index))
                    .orElse(null);
            if (offerKey == null || expectedProductKey == null) {
                return false;
            }
            offerKeys.add(offerKey);
            expectedProductKeys.add(expectedProductKey);
        }

        Map<String, AgentArtifactReference> latestByOfferKey = new LinkedHashMap<>();
        artifactRepository.findByConversationIdAndOfferKeyInOrderByCreatedAtDesc(
                        context.conversationId(),
                        offerKeys.stream().distinct().toList()
                )
                .forEach(reference -> latestByOfferKey.putIfAbsent(reference.getOfferKey(), reference));
        for (int index = 0; index < offerKeys.size(); index++) {
            if (!offerBelongsToProduct(
                    latestByOfferKey.get(offerKeys.get(index)),
                    offerKeys.get(index),
                    expectedProductKeys.get(index)
            )) {
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
            boolean matches = expectedProductKey(context, evidence, ordinals.get(index))
                    .map(reference -> reference.equals(actual))
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
            return expectedProductKey(context, evidence, ordinals.getFirst())
                    .map(reference -> reference.equals(productKey))
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
        for (List<AgentArtifactReference> productSet : productSets(evidence)) {
            Optional<AgentArtifactReference> candidate = productSet.stream()
                    .filter(reference -> reference.getOrdinal() == ordinal)
                    .findFirst();
            if (candidate.isEmpty()) {
                continue;
            }
            if (turnTokens.isEmpty()
                    || descriptiveTokens(candidate.get().getLabel()).containsAll(turnTokens)) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private Optional<String> expectedProductKey(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        List<AgentArtifactReference> currentRunEvidence = evidence.stream()
                .filter(reference -> Objects.equals(reference.getRunId(), context.runId()))
                .toList();
        boolean currentRunIssuedOrdinal = productSets(currentRunEvidence).stream()
                .flatMap(List::stream)
                .anyMatch(reference -> reference.getOrdinal() == ordinal);
        if (currentRunIssuedOrdinal) {
            return expectedProduct(context, currentRunEvidence, ordinal)
                    .map(AgentArtifactReference::getCanonicalProductKey);
        }
        if (context.visibleProductContext() != null) {
            return context.visibleProductContext().products().stream()
                    .filter(product -> product.visibleOrdinal() == ordinal)
                    .filter(product -> {
                        Set<String> turnTokens = descriptiveTokens(context.triggeringUserText());
                        return turnTokens.isEmpty()
                                || descriptiveTokens(product.title()).containsAll(turnTokens);
                    })
                    .map(product -> product.canonicalProductKey())
                    .filter(Objects::nonNull)
                    .findFirst();
        }
        return expectedProduct(context, evidence, ordinal)
                .map(AgentArtifactReference::getCanonicalProductKey);
    }

    private Optional<AgentCartSnapshotSupport.CartLine> expectedCartLine(
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        List<AgentCartSnapshotSupport.CartSnapshot> cartsWithLines =
                currentCartSnapshots(evidence, cartSnapshotSupport).stream()
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
            List<AgentArtifactReference> carts = currentCartSnapshots(evidence, cartSnapshotSupport).stream()
                    .map(AgentCartSnapshotSupport.CartSnapshot::artifact)
                    .toList();
            return atOrdinal(carts, ordinal);
        }
        return atOrdinal(latestArtifactSet(evidence, type), ordinal);
    }

    private Optional<AgentArtifactReference> expectedInventoryItem(
            List<AgentArtifactReference> evidence,
            int ordinal
    ) {
        return atOrdinal(latestInventoryResultSet(evidence), ordinal);
    }

    private List<AgentArtifactReference> latestInventoryResultSet(
            List<AgentArtifactReference> evidence
    ) {
        return AgentArtifactEvidenceSupport.latestInventoryResultSet(evidence, objectMapper);
    }

    private boolean mustUseCurrentCartForReadd(
            AgentToolExecutionContext context,
            List<AgentArtifactReference> evidence
    ) {
        String turn = Optional.ofNullable(context.triggeringUserText()).orElse("");
        return isReaddReference(turn)
                && mostRecentlyRemovedLine(evidence, cartSnapshotSupport).isPresent();
    }

    private boolean offerBelongsToProduct(UUID conversationId, String offerKey, String canonicalProductKey) {
        if (offerKey == null || canonicalProductKey == null) {
            return false;
        }
        return artifactRepository.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                        conversationId, offerKey)
                .filter(reference -> offerBelongsToProduct(reference, offerKey, canonicalProductKey))
                .isPresent();
    }

    private boolean offerBelongsToProduct(
            AgentArtifactReference reference,
            String offerKey,
            String canonicalProductKey
    ) {
        return reference != null
                && (reference.getArtifactType() == AgentArtifactType.OFFER
                || reference.getArtifactType() == AgentArtifactType.PRODUCT
                || reference.getArtifactType() == AgentArtifactType.SAVED_PRODUCT)
                && offerKey.equals(reference.getOfferKey())
                && canonicalProductKey.equals(reference.getCanonicalProductKey());
    }

    private List<AgentArtifactReference> recent(UUID conversationId) {
        return artifactRepository.findByConversationIdOrderByCreatedAtDescOrdinalAsc(
                conversationId, PageRequest.of(0, REFERENCE_WINDOW));
    }

    private boolean single(List<Integer> ordinals) {
        return ordinals.size() == 1;
    }

}
