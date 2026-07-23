package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.service.AgentTargetJsonSupport.arrayField;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.arrayValues;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.nestedTextValues;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.stringValues;
import static com.meant.api.module.agent.service.AgentTargetJsonSupport.text;

import com.meant.api.module.agent.entity.ShoppingMission;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AgentMissionTargetSupport {

    private AgentMissionTargetSupport() {
    }

    static boolean matchesDelegatedMission(
            ShoppingMission mission,
            String toolName,
            String canonicalArgumentsJson,
            ObjectMapper objectMapper
    ) {
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            Set<String> selectedOffers = nestedTextValues(
                    objectMapper,
                    mission.getCoverageJson(),
                    "offerKey"
            );
            Set<String> missionCartIds = stringValues(objectMapper, mission.getCartReferencesJson());
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
                        && !stringValues(objectMapper, mission.getCheckoutReferencesJson()).isEmpty();
                default -> false;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean matchesMissionTarget(
            ShoppingMission mission,
            String canonicalArgumentsJson,
            ObjectMapper objectMapper
    ) {
        if (mission == null || mission.getId() == null) {
            return false;
        }
        try {
            JsonNode arguments = objectMapper.readTree(canonicalArgumentsJson);
            return mission.getId().toString().equals(text(arguments, "missionId"));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
