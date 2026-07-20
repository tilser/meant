package com.meant.api.module.agent.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Converts an unresolved user product target into a deterministic clarification before tools can run.
 */
@Service
@RequiredArgsConstructor
public class AgentProductClarificationService {

    private static final List<String> PRODUCT_SELECTION_TOOL_PRIORITY = List.of(
            "unpin_product",
            "unwatch_product",
            "pin_product",
            "watch_product",
            "prepare_carts",
            "add_cart_line"
    );
    private static final Set<String> PRODUCT_SELECTION_TOOLS = Set.of(
            "unpin_product", "unwatch_product", "pin_product", "watch_product",
            "prepare_carts", "add_cart_line",
            "get_product", "get_product_reviews", "find_discount_codes", "find_similar_products"
    );
    private static final Pattern PRODUCT_DETAIL_INTENT = Pattern.compile(
            "\\b(?:about|detail|details|inspect|show|tell|what)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRODUCT_REVIEW_INTENT = Pattern.compile(
            "\\breviews?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DISCOUNT_CODE_INTENT = Pattern.compile(
            "\\b(?:code|codes|coupon|coupons|discount|discounts|promo|promos)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SIMILAR_PRODUCT_INTENT = Pattern.compile(
            "\\bsimilar\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAXIMUM_TITLE_LENGTH = 120;

    private final AgentToolRegistry toolRegistry;
    private final AgentToolAuthorizationPolicy authorizationPolicy;
    private final AgentMutationTargetPolicy mutationTargetPolicy;

    /**
     * Checks every proposed call before the coordinator starts any of them. The target policy deliberately evaluates
     * only the user's wording here, so a wrong model argument does not cause an unnecessary user question.
     */
    public Optional<AgentProductClarification> preflight(
            AgentToolExecutionContext context,
            List<AgentModelToolCall> calls
    ) {
        if (context == null || calls == null || calls.isEmpty()) {
            return Optional.empty();
        }
        for (AgentModelToolCall call : calls) {
            if (call == null || !PRODUCT_SELECTION_TOOLS.contains(call.name())) {
                continue;
            }
            if (!relevantProductAction(context, call.name())) {
                continue;
            }
            AgentToolDescriptor descriptor = descriptor(call.name()).orElse(null);
            if (descriptor == null || !authorizationPolicy.authorized(context, descriptor)) {
                continue;
            }
            if (authorizationPolicy.isUnqualifiedDelegatedCartAddition(context, call.name())) {
                continue;
            }
            if (!mutationTargetPolicy.requiresProductClarification(context, call.name())) {
                continue;
            }
            List<AgentVisibleProductReference> products = candidates(context, call.name());
            if (products.isEmpty()) {
                continue;
            }
            return Optional.of(clarification(context, call.name(), products));
        }
        return Optional.empty();
    }

    /**
     * Detects an authorized product action whose user-authored target is unresolved. This does not depend on the
     * model producing a tool call or following the WAITING_FOR_USER response convention.
     */
    public Optional<AgentProductClarification> unresolvedIntent(AgentToolExecutionContext context) {
        if (context == null) {
            return Optional.empty();
        }
        return PRODUCT_SELECTION_TOOL_PRIORITY.stream()
                .map(this::descriptor)
                .flatMap(Optional::stream)
                .filter(descriptor -> authorizationPolicy.authorized(context, descriptor))
                .filter(descriptor -> !authorizationPolicy.isUnqualifiedDelegatedCartAddition(
                        context, descriptor.name()))
                .filter(descriptor -> mutationTargetPolicy.requiresProductClarification(
                        context, descriptor.name()))
                .findFirst()
                .flatMap(descriptor -> {
                    List<AgentVisibleProductReference> products = candidates(context, descriptor.name());
                    return products.isEmpty()
                            ? Optional.empty()
                            : Optional.of(clarification(context, descriptor.name(), products));
                });
    }

    public String question(AgentProductClarification clarification) {
        StringBuilder question = new StringBuilder(actionQuestion(clarification.toolName()))
                .append(" Reply with a number or product name:\n");
        for (AgentVisibleProductReference product : clarification.products()) {
            question.append(product.visibleOrdinal())
                    .append(". ")
                    .append(displayTitle(product))
                    .append('\n');
        }
        return question.toString().stripTrailing();
    }

    private AgentProductClarification clarification(
            AgentToolExecutionContext context,
            String toolName,
            List<AgentVisibleProductReference> products
    ) {
        AgentProductClarification pending = context.pendingProductClarification();
        String originalUserText = pending != null && pending.continuesWith(toolName)
                ? pending.originalUserText()
                : context.triggeringUserText();
        return new AgentProductClarification(toolName, originalUserText, products);
    }

    private List<AgentVisibleProductReference> candidates(AgentToolExecutionContext context, String toolName) {
        return validCandidates(mutationTargetPolicy.productClarificationCandidates(context, toolName));
    }

    private List<AgentVisibleProductReference> validCandidates(List<AgentVisibleProductReference> products) {
        if (products == null) {
            return List.of();
        }
        Set<Integer> ordinals = new HashSet<>();
        Set<String> productKeys = new HashSet<>();
        return products.stream()
                .filter(product -> product != null
                        && product.visibleOrdinal() > 0
                        && present(product.canonicalProductKey()))
                .sorted(Comparator.comparingInt(AgentVisibleProductReference::visibleOrdinal))
                .filter(product -> ordinals.add(product.visibleOrdinal())
                        && productKeys.add(product.canonicalProductKey()))
                .map(product -> new AgentVisibleProductReference(
                        product.visibleOrdinal(),
                        product.resultOrdinal(),
                        product.canonicalProductKey(),
                        product.recommendedOfferKey(),
                        present(product.title())
                                ? product.title().replaceAll("\\s+", " ").trim()
                                : "Product " + product.visibleOrdinal()
                ))
                .toList();
    }

    private Optional<AgentToolDescriptor> descriptor(String toolName) {
        try {
            return Optional.of(toolRegistry.required(toolName).descriptor());
        } catch (AgentException exception) {
            return Optional.empty();
        }
    }

    private boolean relevantProductAction(AgentToolExecutionContext context, String toolName) {
        if (PRODUCT_SELECTION_TOOL_PRIORITY.contains(toolName)) {
            return true;
        }
        if (context.pendingProductClarification() != null
                && toolName.equals(context.pendingProductClarification().toolName())) {
            return true;
        }
        String turn = context.triggeringUserText() == null ? "" : context.triggeringUserText();
        return switch (toolName) {
            case "get_product" -> PRODUCT_DETAIL_INTENT.matcher(turn).find();
            case "get_product_reviews" -> PRODUCT_REVIEW_INTENT.matcher(turn).find();
            case "find_discount_codes" -> DISCOUNT_CODE_INTENT.matcher(turn).find();
            case "find_similar_products" -> SIMILAR_PRODUCT_INTENT.matcher(turn).find();
            default -> false;
        };
    }

    private String actionQuestion(String toolName) {
        return switch (toolName) {
            case "prepare_carts", "add_cart_line" -> "Which product should I add to your cart?";
            case "pin_product" -> "Which product should I pin?";
            case "unpin_product" -> "Which product should I unpin?";
            case "watch_product" -> "Which product should I watch?";
            case "unwatch_product" -> "Which product should I stop watching?";
            case "get_product" -> "Which product would you like details for?";
            case "get_product_reviews" -> "Which product would you like reviews for?";
            case "find_discount_codes" -> "Which product should I find discount codes for?";
            case "find_similar_products" -> "Which product should I find similar products for?";
            default -> "Which product did you mean?";
        };
    }

    private String displayTitle(AgentVisibleProductReference product) {
        String title = present(product.title())
                ? product.title().replaceAll("\\s+", " ").trim()
                : "Product " + product.visibleOrdinal();
        if (title.length() > MAXIMUM_TITLE_LENGTH) {
            title = title.substring(0, MAXIMUM_TITLE_LENGTH - 1).stripTrailing() + "…";
        }
        return title;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
