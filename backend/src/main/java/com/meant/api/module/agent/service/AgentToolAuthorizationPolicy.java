package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    private static final Pattern EXPLICIT_RESULT_ORDINAL = Pattern.compile(
            "\\b(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|"
                    + "1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ShoppingMissionRepository missionRepository;
    private final AgentMutationTargetPolicy mutationTargetPolicy;

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
        String turn = normalize(context.triggeringUserText());
        if (name.startsWith("create_shopping_mission")
                || name.startsWith("update_shopping_mission")
                || name.startsWith("evaluate_mission_coverage")) {
            return true;
        }
        if (PRODUCT_STATE_MUTATIONS.contains(name)) {
            return !negated(turn) && productStateAuthorized(name, turn);
        }
        if (CART_ADDITIONS.contains(name)) {
            return !negated(turn) && (cartAdditionAuthorized(turn) || delegatedMission(context, turn).isPresent());
        }
        if (CART_UPDATES.contains(name)) {
            return !negated(turn) && (cartUpdateAuthorized(name, turn) || delegatedMission(context, turn).isPresent());
        }
        if (CHECKOUT_MUTATIONS.contains(name)) {
            return !negated(turn) && (checkoutAuthorized(name, turn) || delegatedMission(context, turn).isPresent());
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
        if (context.runId() == null || missionPlanningTool(descriptor.name())) {
            return true;
        }
        if (descriptor.riskClass() == AgentToolRisk.READ) {
            return mutationTargetPolicy.matchesExplicitOrdinal(
                    context, descriptor.name(), canonicalArgumentsJson);
        }
        Optional<ShoppingMission> delegated = delegatedMission(context, normalize(context.triggeringUserText()));
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

    private boolean productStateAuthorized(String toolName, String turn) {
        return switch (toolName) {
            case "pin_product" -> containsWord(turn, "pin");
            case "unpin_product" -> containsWord(turn, "unpin") || containsAny(turn, "remove pin", "stop pinning");
            case "watch_product" -> !containsAny(turn, "stop watching")
                    && !containsWord(turn, "unwatch")
                    && (containsWord(turn, "watch") || containsWord(turn, "watching"));
            case "unwatch_product" -> containsWord(turn, "unwatch") || containsAny(turn, "stop watching");
            default -> false;
        };
    }

    private boolean cartAdditionAuthorized(String turn) {
        if (!EXPLICIT_RESULT_ORDINAL.matcher(turn).find()) {
            return false;
        }
        return containsAny(turn, "add to cart", "add it to", "put in cart", "put it in", "build my cart",
                "prepare my cart", "prepare the cart", "cart these")
                || containsAny(turn, "add ", "buy ", "take ", "get ");
    }

    private boolean cartUpdateAuthorized(String toolName, String turn) {
        if ("remove_cart_line".equals(toolName)) {
            return containsWord(turn, "remove") || containsWord(turn, "delete") || containsAny(turn, "take out");
        }
        return containsAny(
                turn,
                "change the quantity", "update the quantity", "increase", "decrease", "make it ", "set quantity"
        );
    }

    private boolean checkoutAuthorized(String toolName, String turn) {
        if ("update_checkout".equals(toolName)) {
            return containsAny(
                    turn,
                    "shipping address", "delivery address", "billing address", "contact details",
                    "phone number", "email address", "apply the code", "apply code"
            );
        }
        return containsAny(
                turn,
                "checkout", "check out", "proceed", "ready to order", "ready to pay", "prepare payment"
        );
    }

    private Optional<ShoppingMission> delegatedMission(AgentToolExecutionContext context, String turn) {
        if (!containsAny(
                turn,
                "prepare everything", "handle everything", "build the bundle", "build my cart",
                "go ahead", "proceed", "continue", "finish the mission"
        )) {
            return Optional.empty();
        }
        return missionRepository.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(
                        context.conversationId(), context.userId())
                .filter(mission -> mission.getStatus() != ShoppingMissionStatus.CANCELLED
                        && mission.getStatus() != ShoppingMissionStatus.COMPLETED
                        && (mission.getStatus() == ShoppingMissionStatus.READY
                        || mission.getStatus() == ShoppingMissionStatus.CHECKOUT_PREPARED));
    }

    private boolean missionPlanningTool(String name) {
        return name.startsWith("create_shopping_mission")
                || name.startsWith("update_shopping_mission")
                || name.startsWith("evaluate_mission_coverage");
    }

    private boolean negated(String turn) {
        return containsAny(
                turn,
                " don't ", " do not ", " never ", " avoid ", " not add", " not buy", " not pin", " not watch",
                " without adding", " without checkout", " without checking out"
        );
    }

    private String normalize(String value) {
        return value == null ? "" : " " + value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWord(String value, String word) {
        return Pattern.compile("(?:^|[^a-z0-9_])" + Pattern.quote(word) + "(?:$|[^a-z0-9_])")
                .matcher(value)
                .find();
    }
}
