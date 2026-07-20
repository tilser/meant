package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentToolAuthorizationPolicy {

    private static final Set<String> PRODUCT_STATE_MUTATIONS = Set.of(
            "pin_product", "unpin_product", "watch_product", "unwatch_product"
    );
    private static final Set<String> CART_ADDITIONS = Set.of("prepare_carts", "add_cart_line");
    private static final Set<String> CART_UPDATES = Set.of("update_cart_line", "remove_cart_line");
    private static final Set<String> CHECKOUT_MUTATIONS = Set.of("prepare_checkout", "update_checkout");
    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile(
            "(?:[,.!?;:\\n]+|\\s+[—–]\\s+|\\b(?:but|however)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(?:don't|dont|do not|never|avoid|without|not)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final String MUTATION_ACTION = "(?:add|put|place|buy|purchase|order|pin|save|unpin|"
            + "remove|delete|take\\s+out|stop|watch|unwatch|change|update|increase|decrease|set|apply|use|"
            + "checkout|check\\s+out|prepare|handle|build|finish)";
    private static final Pattern ACTION_REQUEST = Pattern.compile(
            "^(?:(?:okay|ok|sure|yes|now|then|also)\\s+)?(?:please\\s+)?(?:"
                    + MUTATION_ACTION + "\\b"
                    + "|(?:can|could|would|will)\\s+(?:you|we)\\s+(?:please\\s+)?"
                    + MUTATION_ACTION + "\\b"
                    + "|(?:i\\s+(?:want|need)\\s+(?:you\\s+)?to|"
                    + "i(?:['’]d|\\s+would)\\s+like\\s+(?:you\\s+)?to)\\s+"
                    + MUTATION_ACTION + "\\b"
                    + "|(?:let(?:['’]?s|\\s+us)|go\\s+ahead(?:\\s+and)?)\\s+"
                    + "(?:(?:do|start)\\s+)?(?:(?:a|the)\\s+)?"
                    + MUTATION_ACTION + "\\b"
                    + "|(?:i(?:['’]m|\\s+am)\\s+)?ready\\s+to\\s+(?:order|pay)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DISCOVERY_THEN_ACTION_PREFIX = Pattern.compile(
            "^(?:(?:okay|ok|sure|yes|now|then|also)\\s+)?(?:please\\s+)?"
                    + "(?:(?:can|could|would|will)\\s+you\\s+(?:please\\s+)?)?"
                    + "(?:find|search(?:\\s+for)?|look\\s+for|show|choose|pick|get)\\b.*"
                    + "\\b(?:and|then)\\s+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSION_CREATION_INTENT = Pattern.compile(
            "(?:\\b(?:create|start|make|build)\\b.{0,100}\\b(?:shopping\\s+)?"
                    + "(?:mission|checklist|bundle)\\b)"
                    + "|(?:\\b(?:plan|organize|prepare|build)\\b.{0,100}\\b"
                    + "(?:outfit|setup|kit|bundle)\\b)"
                    + "|(?:\\b(?:plan|organize|prepare|build)\\b.{0,100}\\b"
                    + "(?:party|picnic|meal)\\b(?=\\s*(?:$|[.!?]|(?:for|with|in|on|at|of)\\b)))"
                    + "|(?:\\b(?:plan|organize|prepare|find|get|buy|shop\\s+for)\\b.{0,100}\\b"
                    + "(?:everything|all\\s+(?:the\\s+)?(?:items|things|gear|supplies))\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSION_UPDATE_INTENT = Pattern.compile(
            "(?:\\b(?:add|remove|change|update|set|cancel|complete|finish)\\b.*"
                    + "\\b(?:mission|plan|checklist|bundle|outfit)\\b)"
                    + "|(?:\\b(?:evaluate|recalculate)\\b.*\\b(?:mission|coverage)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSION_CREATION_REQUEST = actionRequest(
            "(?:create|start|make|build|plan|organize|prepare|find|get|buy|shop\\s+for)"
    );
    private static final Pattern MISSION_UPDATE_REQUEST = actionRequest(
            "(?:add|remove|change|update|set|cancel|complete|finish|evaluate|recalculate)"
    );
    private static final Pattern PIN_INTENT = word("pin|save");
    private static final Pattern UNPIN_INTENT = Pattern.compile(
            "\\b(?:unpin|remove\\s+(?:the\\s+)?pin|stop\\s+pinning)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WATCH_INTENT = word("watch|watching");
    private static final Pattern UNWATCH_INTENT = Pattern.compile(
            "\\b(?:unwatch|stop\\s+watching)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CART_ADDITION_INTENT = Pattern.compile(
            "(?:\\b(?:add|put|place)\\b.*\\b(?:cart|basket|bag|one|ones|it|them|both|pair|item|items)\\b)"
                    + "|(?:\\b(?:buy|purchase|order)\\b.*"
                    + "\\b(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|"
                    + "1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th|one|ones|it|them|both|pair|item|items)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CART_REMOVAL_INTENT = Pattern.compile(
            "\\b(?:remove|delete|take\\s+out)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CART_ITEM_SELECTION = Pattern.compile(
            "^(?:the\\s+)?(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|"
                    + "1(?:st)?|2(?:nd)?|3(?:rd)?|4(?:th)?|5(?:th)?|6(?:th)?|7(?:th)?|8(?:th)?|"
                    + "9(?:th)?|10(?:th)?)(?:\\s+(?:one|item|line))?[.!]?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final String CART_REMOVAL_CLARIFICATION = "Which cart item should I remove?";
    private static final Pattern CART_UPDATE_INTENT = Pattern.compile(
            "\\b(?:change|update|increase|decrease|set)\\b.*\\b(?:quantity|amount|count|line|item)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHECKOUT_UPDATE_INTENT = Pattern.compile(
            "\\b(?:apply|change|set|update|use)\\b.*\\b(?:shipping|delivery|billing|contact|phone|email|code)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHECKOUT_PREPARATION_INTENT = Pattern.compile(
            "\\b(?:checkout|check\\s+out|ready\\s+to\\s+(?:order|pay)|prepare\\s+payment)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSION_DELEGATION_INTENT = Pattern.compile(
            "\\b(?:prepare\\s+everything|handle\\s+everything|build\\s+(?:the\\s+bundle|my\\s+cart)|"
                    + "finish\\s+the\\s+mission)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPLICIT_PRODUCT_ORDINAL = Pattern.compile(
            "\\b(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|"
                    + "1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th|"
                    + "(?:product|item|option|number)\\s+(?:10|[1-9]))\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPLICIT_PRODUCT_SELECTION_ACTION = Pattern.compile(
            "\\b(?:add|put|place|buy|purchase|order|choose|pick|select|prefer|want|take|get|use|include)\\b"
                    + "|\\bgo\\s+with\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BARE_PRODUCT_NUMBER = Pattern.compile(
            "(?:^|[,.!?;:\\n]+)\\s*#?(?:10|[1-9])(?:[.)])?(?=\\s|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTEXTUAL_PRODUCT_QUALIFIER = Pattern.compile(
            "\\b(?:except|excluding|exclude|without|other\\s+than|instead\\s+of|only)\\b"
                    + "|\\b(?:the|this|that|these|those)\\b"
                    + "(?:\\s+[\\p{L}\\p{N}'’#-]+){0,6}\\s+"
                    + "(?:one|ones|item|items|product|products|option|options)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UNQUALIFIED_MISSION_SUFFIX = Pattern.compile(
            "^(?:(?:(?:that\\s+)?(?:i|we)\\s+need|needed)(?:\\s+for\\b.*)?|for\\b.+)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSION_DELEGATION_CONTINUATION = Pattern.compile(
            "^(?:please\\s+)?(?:go\\s+ahead|proceed|continue)(?:\\s+(?:with|through)\\s+(?:the\\s+)?"
                    + "(?:mission|plan|bundle|cart|checkout))?[.!]?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> MISSION_PURPOSE_FILLER = Set.of(
            "a", "an", "for", "i", "my", "need", "needed", "our", "please", "that", "the", "to", "we",
            "your"
    );
    private static final Pattern SHORT_APPROVAL = Pattern.compile(
            "^(?:please\\s+)?(?:go\\s+ahead|proceed|continue)(?:\\s+with\\s+(?:the\\s+)?"
                    + "(?:mission|plan|bundle|cart|checkout))?[.!]?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSION_APPROVAL = Pattern.compile(
            "^(?:please\\s+)?(?:go\\s+ahead|proceed|continue)\\s+(?:with\\s+)?(?:the\\s+)?"
                    + "(?:mission|plan|bundle)[.!]?$",
            Pattern.CASE_INSENSITIVE
    );

    private final ShoppingMissionRepository missionRepository;
    private final AgentMutationTargetPolicy mutationTargetPolicy;
    private final AgentMessageRepository messageRepository;

    public List<AgentToolDescriptor> available(
            AgentToolExecutionContext context,
            List<AgentToolDescriptor> descriptors
    ) {
        return descriptors.stream().filter(descriptor -> authorized(context, descriptor)).toList();
    }

    public boolean authorized(AgentToolExecutionContext context, AgentToolDescriptor descriptor) {
        if (context.runId() == null || descriptor.riskClass() == AgentToolRisk.READ) {
            return true;
        }
        String name = descriptor.name();
        String turn = authorizationTurn(context, name);
        if (name.startsWith("create_shopping_mission")) {
            return missionCreationAuthorized(turn) && activeMission(context).isEmpty();
        }
        if (name.startsWith("update_shopping_mission")
                || name.startsWith("evaluate_mission_coverage")) {
            return activeMission(context).isPresent() && missionContinuationAuthorized(turn);
        }
        if (PRODUCT_STATE_MUTATIONS.contains(name)) {
            return productStateAuthorized(name, turn);
        }
        if (CART_ADDITIONS.contains(name)) {
            return positiveClause(turn, CART_ADDITION_INTENT)
                    || discoveryThenActionClause(turn, CART_ADDITION_INTENT)
                    || delegatedMission(context, turn).isPresent();
        }
        if (CART_UPDATES.contains(name)) {
            return cartUpdateAuthorized(context, name, turn) || delegatedMission(context, turn).isPresent();
        }
        if (CHECKOUT_MUTATIONS.contains(name)) {
            return checkoutAuthorized(name, turn) || delegatedMission(context, turn).isPresent();
        }
        return false;
    }

    public boolean authorizedInvocation(
            AgentToolExecutionContext context,
            AgentToolDescriptor descriptor,
            String canonicalArgumentsJson
    ) {
        if (!authorized(context, descriptor)) {
            return false;
        }
        if (context.runId() == null || descriptor.name().startsWith("create_shopping_mission")) {
            return true;
        }
        if (descriptor.name().startsWith("update_shopping_mission")
                || descriptor.name().startsWith("evaluate_mission_coverage")) {
            return activeMission(context)
                    .filter(mission -> mutationTargetPolicy.matchesMissionTarget(mission, canonicalArgumentsJson))
                    .isPresent();
        }
        if (descriptor.riskClass() == AgentToolRisk.READ) {
            return mutationTargetPolicy.matchesExplicitOrdinal(
                    context, descriptor.name(), canonicalArgumentsJson);
        }
        Optional<ShoppingMission> delegated = delegatedMission(
                context,
                authorizationTurn(context, descriptor.name())
        );
        if (delegated.isPresent()
                && (CART_ADDITIONS.contains(descriptor.name())
                || CART_UPDATES.contains(descriptor.name())
                || CHECKOUT_MUTATIONS.contains(descriptor.name()))) {
            return mutationTargetPolicy.matchesDelegatedMission(
                    delegated.get(), descriptor.name(), canonicalArgumentsJson);
        }
        return mutationTargetPolicy.matchesMutationTarget(
                context, descriptor.name(), canonicalArgumentsJson);
    }

    /**
     * Identifies an unqualified, mission-wide cart addition. Explicit product targets and pending choices keep the
     * generic product-choice guard; {@link #authorizedInvocation(AgentToolExecutionContext, AgentToolDescriptor,
     * String)} still validates every proposed offer and cart against the delegated mission before execution.
     */
    public boolean isUnqualifiedDelegatedCartAddition(
            AgentToolExecutionContext context,
            String toolName
    ) {
        if (context == null
                || context.pendingProductClarification() != null
                || !CART_ADDITIONS.contains(toolName)) {
            return false;
        }
        String turn = authorizationTurn(context, toolName);
        if (positiveClause(turn, CART_ADDITION_INTENT)
                || discoveryThenActionClause(turn, CART_ADDITION_INTENT)
                || EXPLICIT_PRODUCT_ORDINAL.matcher(turn).find()
                || EXPLICIT_PRODUCT_SELECTION_ACTION.matcher(turn).find()
                || BARE_PRODUCT_NUMBER.matcher(turn).find()
                || CONTEXTUAL_PRODUCT_QUALIFIER.matcher(turn).find()
                || mentionsVisibleProduct(context, turn)) {
            return false;
        }
        return delegatedMission(context, turn)
                .filter(mission -> hasOnlyUnqualifiedMissionWording(turn, mission))
                .isPresent();
    }

    private boolean hasOnlyUnqualifiedMissionWording(String turn, ShoppingMission mission) {
        boolean foundDelegation = false;
        for (String clause : CLAUSE_BOUNDARY.split(turn)) {
            String candidate = clause.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            Matcher delegation = MISSION_DELEGATION_INTENT.matcher(candidate);
            if (delegation.find() && positiveClause(candidate, MISSION_DELEGATION_INTENT)) {
                String suffix = candidate.substring(delegation.end()).strip();
                if (foundDelegation
                        || !UNQUALIFIED_MISSION_SUFFIX.matcher(suffix).matches()
                        || !purposeMatchesMission(suffix, mission)) {
                    return false;
                }
                foundDelegation = true;
                continue;
            }
            if (!MISSION_DELEGATION_CONTINUATION.matcher(candidate).matches()) {
                return false;
            }
        }
        return foundDelegation;
    }

    private boolean purposeMatchesMission(String suffix, ShoppingMission mission) {
        Set<String> purposeTokens = tokens(suffix);
        purposeTokens.removeAll(MISSION_PURPOSE_FILLER);
        if (purposeTokens.isEmpty()) {
            return true;
        }
        return mission.getGoal() != null && tokens(mission.getGoal()).containsAll(purposeTokens);
    }

    private Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = TOKEN.matcher(normalize(value).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private boolean mentionsVisibleProduct(AgentToolExecutionContext context, String turn) {
        if (context.visibleProductContext() == null || turn == null || turn.isBlank()) {
            return false;
        }
        String normalizedTurn = normalize(turn).toLowerCase(Locale.ROOT);
        return context.visibleProductContext().products().stream()
                .filter(Objects::nonNull)
                .anyMatch(product -> mentionsVisibleProduct(normalizedTurn, product));
    }

    private boolean mentionsVisibleProduct(String normalizedTurn, AgentVisibleProductReference product) {
        return containsNormalized(normalizedTurn, product.canonicalProductKey())
                || containsNormalized(normalizedTurn, product.recommendedOfferKey())
                || containsNormalized(normalizedTurn, product.title());
    }

    private boolean containsNormalized(String normalizedTurn, String candidate) {
        return candidate != null
                && !candidate.isBlank()
                && normalizedTurn.contains(normalize(candidate).toLowerCase(Locale.ROOT));
    }

    private boolean productStateAuthorized(String toolName, String turn) {
        return switch (toolName) {
            case "pin_product" -> positiveClause(turn, PIN_INTENT)
                    && !positiveClause(turn, UNPIN_INTENT);
            case "unpin_product" -> positiveClause(turn, UNPIN_INTENT);
            case "watch_product" -> positiveClause(turn, WATCH_INTENT)
                    && !positiveClause(turn, UNWATCH_INTENT);
            case "unwatch_product" -> positiveClause(turn, UNWATCH_INTENT);
            default -> false;
        };
    }

    private boolean cartUpdateAuthorized(AgentToolExecutionContext context, String toolName, String turn) {
        if ("remove_cart_line".equals(toolName)) {
            return positiveClause(turn, CART_REMOVAL_INTENT)
                    || answersCartRemovalClarification(context, turn);
        }
        return positiveClause(turn, CART_UPDATE_INTENT);
    }

    private boolean answersCartRemovalClarification(AgentToolExecutionContext context, String turn) {
        if (context.triggeringMessageId() == null || !CART_ITEM_SELECTION.matcher(turn).matches()) {
            return false;
        }
        return messageRepository.findById(context.triggeringMessageId())
                .flatMap(triggering -> messageRepository
                        .findFirstByConversationIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                                context.conversationId(), triggering.getSequenceNumber()))
                .filter(message -> message.getRole() == AgentMessageRole.ASSISTANT)
                .map(AgentMessage::getTextContent)
                .filter(Objects::nonNull)
                .map(text -> text.lines().findFirst().orElse(""))
                .filter(CART_REMOVAL_CLARIFICATION::equals)
                .isPresent();
    }

    private boolean checkoutAuthorized(String toolName, String turn) {
        if ("update_checkout".equals(toolName)) {
            return positiveClause(turn, CHECKOUT_UPDATE_INTENT);
        }
        return positiveClause(turn, CHECKOUT_PREPARATION_INTENT) || shortApproval(turn);
    }

    private Optional<ShoppingMission> delegatedMission(AgentToolExecutionContext context, String turn) {
        if (!positiveClause(turn, MISSION_DELEGATION_INTENT) && !shortApproval(turn)) {
            return Optional.empty();
        }
        return activeMission(context)
                .filter(mission -> mission.getStatus() == ShoppingMissionStatus.READY
                        || mission.getStatus() == ShoppingMissionStatus.CHECKOUT_PREPARED);
    }

    private Optional<ShoppingMission> activeMission(AgentToolExecutionContext context) {
        return missionRepository.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(
                        context.conversationId(), context.userId())
                .filter(mission -> mission.getStatus() != ShoppingMissionStatus.CANCELLED
                        && mission.getStatus() != ShoppingMissionStatus.COMPLETED);
    }

    private String authorizationTurn(AgentToolExecutionContext context, String toolName) {
        if (context.pendingProductClarification() != null
                && context.pendingProductClarification().continuesWith(toolName)
                && mutationTargetPolicy.isPendingProductSelectionAnswer(context, toolName)) {
            return normalize(context.pendingProductClarification().originalUserText());
        }
        return normalize(context.triggeringUserText());
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean missionCreationAuthorized(String value) {
        return requestedPatternClause(value, MISSION_CREATION_INTENT, MISSION_CREATION_REQUEST);
    }

    private boolean missionContinuationAuthorized(String value) {
        return missionCreationAuthorized(value)
                || requestedPatternClause(value, MISSION_UPDATE_INTENT, MISSION_UPDATE_REQUEST)
                || positiveClause(value, MISSION_DELEGATION_INTENT)
                || MISSION_APPROVAL.matcher(value).matches();
    }

    private boolean positiveClause(String value, Pattern intent) {
        for (String clause : CLAUSE_BOUNDARY.split(value)) {
            String candidate = clause.strip();
            if (intent.matcher(candidate).find()
                    && !NEGATION.matcher(candidate).find()
                    && ACTION_REQUEST.matcher(candidate).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean discoveryThenActionClause(String value, Pattern intent) {
        for (String clause : CLAUSE_BOUNDARY.split(value)) {
            String candidate = clause.strip();
            if (NEGATION.matcher(candidate).find()) {
                continue;
            }
            Matcher prefix = DISCOVERY_THEN_ACTION_PREFIX.matcher(candidate);
            if (!prefix.find()) {
                continue;
            }
            String requestedAction = candidate.substring(prefix.end()).strip();
            if (ACTION_REQUEST.matcher(requestedAction).find()
                    && intent.matcher(requestedAction).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean requestedPatternClause(String value, Pattern intent, Pattern actionRequest) {
        for (String clause : CLAUSE_BOUNDARY.split(value)) {
            String candidate = clause.strip();
            if (intent.matcher(candidate).find()
                    && !NEGATION.matcher(candidate).find()
                    && actionRequest.matcher(candidate).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean shortApproval(String value) {
        return SHORT_APPROVAL.matcher(value).matches() && !NEGATION.matcher(value).find();
    }

    private static Pattern word(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    private static Pattern actionRequest(String action) {
        return Pattern.compile(
                "^(?:(?:okay|ok|sure|yes|now|then|also)\\s+)?(?:please\\s+)?(?:"
                        + action + "\\b"
                        + "|(?:can|could|would|will)\\s+(?:you|we)\\s+(?:please\\s+)?"
                        + action + "\\b"
                        + "|(?:i\\s+(?:want|need)\\s+(?:you\\s+)?to|"
                        + "i(?:['’]d|\\s+would)\\s+like\\s+(?:you\\s+)?to)\\s+"
                        + action + "\\b"
                        + "|(?:let(?:['’]?s|\\s+us)|go\\s+ahead(?:\\s+and)?)\\s+"
                        + "(?:(?:do|start)\\s+)?(?:(?:a|the)\\s+)?"
                        + action + "\\b)",
                Pattern.CASE_INSENSITIVE
        );
    }
}
