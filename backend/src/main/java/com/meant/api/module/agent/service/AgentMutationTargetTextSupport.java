package com.meant.api.module.agent.service;

import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Pure text parsing used by {@link AgentMutationTargetPolicy}.
 *
 * <p>Keeping these language heuristics separate from artifact and cart resolution makes it possible to
 * exercise them without constructing the persistence-facing policy.
 */
final class AgentMutationTargetTextSupport {

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
    private static final Pattern BARE_CLARIFICATION_NUMBER = Pattern.compile(
            "^\\s*(?:(?:option|number)\\s+)?([1-9]|10)[.)]?(?:\\s+(.*?))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LEADING_ADDITIONAL_CLARIFICATION_NUMBER = Pattern.compile(
            "^(?:(?:option|number)\\s+)?([1-9]|10)(?:[.)])?(?:\\s|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ADDITIONAL_CLARIFICATION_NUMBER = Pattern.compile(
            "(?:\\b(?:and|or)\\b|[,/&])\\s*(?:(?:option|number)\\s+)?([1-9]|10)\\b",
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
    private static final Pattern PENDING_CLARIFICATION_CANCEL_OR_CHANGE = Pattern.compile(
            "\\b(?:never\\s*mind|cancel|forget\\s+it|stop|no\\s+thanks|not\\s+anymore)\\b"
                    + "|^\\s*(?:no|none|neither)[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PENDING_EXPLICIT_ACTION = Pattern.compile(
            "^(?:(?:okay|ok|actually|now|then|instead)\\s+)?(?:please\\s+)?(?:"
                    + "find|search|show|compare|recommend|start|help|tell|explain|get|open|"
                    + "add|put|place|buy|purchase|order|pin|save|unpin|remove|delete|watch|unwatch|"
                    + "change|update|increase|decrease|set|apply|use|checkout|check\\s+out|prepare)\\b"
                    + "|^(?:can|could|would|will)\\s+(?:you|we)\\s+(?:please\\s+)?(?:"
                    + "find|search|show|compare|recommend|get|add|put|buy|pin|unpin|remove|watch|unwatch)\\b"
                    + "|^i\\s+(?:(?:want|need)\\s+(?:you\\s+)?to|would\\s+like\\s+(?:you\\s+)?to)\\s+(?:"
                    + "find|search|show|compare|recommend|get|add|put|buy|pin|unpin|remove|watch|unwatch)\\b"
                    + "|^(?:don['’]?t|do\\s+not|never)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PENDING_CART_ADDITION_ACTION = pendingAction(
            "add|put|place|buy|purchase|order"
    );
    private static final Pattern PENDING_PIN_ACTION = pendingAction("pin|save");
    private static final Pattern PENDING_UNPIN_ACTION = pendingAction("unpin|remove\\s+(?:the\\s+)?pin");
    private static final Pattern PENDING_WATCH_ACTION = pendingAction("watch");
    private static final Pattern PENDING_UNWATCH_ACTION = pendingAction("unwatch|stop\\s+watching");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> NON_DESCRIPTIVE_TOKENS = Set.of(
            "actually", "add", "again", "already", "also", "and", "are", "back", "bag", "basket", "both", "buy",
            "card", "cards", "choice", "choices",
            "can", "cart", "check", "checkout", "choose", "compare", "could", "delete", "did", "does", "don",
            "dont", "eighth",
            "fifth", "first", "for", "fourth",
            "about", "availability", "available", "cost", "costs", "coupon", "coupons", "detail", "details",
            "discount", "discounts", "find", "four", "from", "get", "give", "how", "info", "information",
            "inspect", "into", "inventory", "item", "items",
            "fine", "good", "great", "look", "looking", "looks", "mean", "meant", "more", "need", "new", "nice",
            "not", "one", "ones",
            "ninth", "know", "like", "may", "might", "mine", "must", "now", "okay", "our", "own", "owned",
            "option", "options", "order", "pair", "pin", "place", "please", "product", "products", "purchase", "put",
            "prepare", "price", "prices", "pricing", "promo", "promos", "remove", "pick", "really", "recommend",
            "recommended", "result", "results", "review", "reviews", "same",
            "save", "search", "second", "set",
            "select", "selected", "seventh", "should", "show", "similar", "sixth", "size", "some", "sounds", "take",
            "tell", "tenth", "that", "the", "them", "then", "these", "third", "this", "those", "three", "two",
            "use", "using", "view", "want", "watch", "what", "which", "will", "with", "would", "you", "your"
    );

    private AgentMutationTargetTextSupport() {
    }

    static List<Integer> ordinals(String userText) {
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
            return IntStream.rangeClosed(1, count).boxed().toList();
        }
        Matcher matcher = ORDINAL.matcher(userText);
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(ordinalValue(matcher.group(1)));
        }
        return List.copyOf(values);
    }

    static List<Integer> clarificationOrdinals(String userText) {
        List<Integer> numbered = numberedClarificationOrdinals(userText);
        return numbered.isEmpty() ? ordinals(userText) : numbered;
    }

    static boolean pendingProductSelectionAttempt(AgentToolExecutionContext context) {
        String userText = Optional.ofNullable(context.triggeringUserText()).orElse("").trim();
        if (userText.isEmpty() || PENDING_CLARIFICATION_CANCEL_OR_CHANGE.matcher(userText).find()) {
            return false;
        }
        if (!numberedClarificationOrdinals(userText).isEmpty()) {
            return true;
        }
        if (PENDING_EXPLICIT_ACTION.matcher(userText).find()) {
            return false;
        }
        List<AgentVisibleProductReference> products = context.pendingProductClarification().products();
        if (!ordinals(userText).isEmpty()) {
            return true;
        }
        if (products.stream().anyMatch(product -> literalReference(userText, product.canonicalProductKey())
                || literalReference(userText, product.recommendedOfferKey()))) {
            return true;
        }
        Set<String> description = descriptiveTokens(userText);
        boolean overlapsVisibleTitle = products.stream()
                .map(AgentVisibleProductReference::title)
                .map(AgentMutationTargetTextSupport::descriptiveTokens)
                .anyMatch(titleTokens -> titleTokens.stream().anyMatch(description::contains));
        if (overlapsVisibleTitle || hasContextualReference(userText)) {
            return true;
        }
        Matcher words = TOKEN.matcher(userText);
        int wordCount = 0;
        while (words.find() && wordCount <= 3) {
            wordCount++;
        }
        return wordCount <= 3;
    }

    static boolean pendingProductClarificationAttempt(
            AgentToolExecutionContext context,
            String toolName
    ) {
        String userText = Optional.ofNullable(context.triggeringUserText()).orElse("").trim();
        if (PENDING_CLARIFICATION_CANCEL_OR_CHANGE.matcher(userText).find()) {
            return false;
        }
        if (pendingProductSelectionAttempt(context)) {
            return true;
        }
        return switch (toolName) {
            case "prepare_carts", "add_cart_line" -> PENDING_CART_ADDITION_ACTION.matcher(userText).find();
            case "pin_product" -> PENDING_PIN_ACTION.matcher(userText).find();
            case "unpin_product" -> PENDING_UNPIN_ACTION.matcher(userText).find();
            case "watch_product" -> PENDING_WATCH_ACTION.matcher(userText).find();
            case "unwatch_product" -> PENDING_UNWATCH_ACTION.matcher(userText).find();
            default -> false;
        };
    }

    static Set<String> descriptiveTokens(String value) {
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

    static int overlap(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }

    static boolean allLiteral(String turn, Set<String> references) {
        return !references.isEmpty() && references.stream().allMatch(reference -> literalReference(turn, reference));
    }

    static boolean literalReference(String turn, String reference) {
        return turn != null && reference != null && !reference.isBlank()
                && turn.toLowerCase(Locale.ROOT).contains(reference.toLowerCase(Locale.ROOT));
    }

    static boolean hasContextualReference(String value) {
        return value != null && CONTEXTUAL_REFERENCE.matcher(value).find();
    }

    static boolean isReaddReference(String turn) {
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
        if (hasContextualReference(turn)) {
            return true;
        }
        return tokens.size() == 1
                || (tokens.size() == 2 && tokens.contains("please"));
    }

    private static List<Integer> numberedClarificationOrdinals(String userText) {
        Matcher bareNumber = BARE_CLARIFICATION_NUMBER.matcher(userText);
        if (!bareNumber.matches()) {
            return List.of();
        }
        int first = Integer.parseInt(bareNumber.group(1));
        String remainder = bareNumber.group(2);
        if (remainder != null) {
            Matcher leadingAdditional = LEADING_ADDITIONAL_CLARIFICATION_NUMBER.matcher(remainder);
            if (leadingAdditional.find()) {
                return List.of(first, Integer.parseInt(leadingAdditional.group(1)));
            }
        }
        Matcher additional = ADDITIONAL_CLARIFICATION_NUMBER.matcher(userText);
        if (additional.find()) {
            return List.of(first, Integer.parseInt(additional.group(1)));
        }
        return List.of(first);
    }

    private static int ordinalValue(String value) {
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

    private static String singularToken(String token) {
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

    private static Pattern pendingAction(String alternatives) {
        return Pattern.compile(
                "^(?:(?:okay|ok|actually|now|then)\\s+)?(?:please\\s+)?(?:" + alternatives + ")\\b"
                        + "|^(?:can|could|would|will)\\s+(?:you|we)\\s+(?:please\\s+)?(?:"
                        + alternatives + ")\\b"
                        + "|^i\\s+(?:(?:want|need)\\s+(?:you\\s+)?to|"
                        + "would\\s+like\\s+(?:you\\s+)?to)\\s+(?:" + alternatives + ")\\b",
                Pattern.CASE_INSENSITIVE
        );
    }
}
