package com.meant.api.module.agent.service.dto;

import java.util.List;

/**
 * Server-authored context that carries an unresolved product choice into the user's next turn.
 */
public record AgentProductClarification(
        String toolName,
        String originalUserText,
        List<AgentVisibleProductReference> products
) {

    public AgentProductClarification {
        products = products == null ? List.of() : List.copyOf(products);
    }

    public boolean continuesWith(String candidateToolName) {
        if (java.util.Objects.equals(toolName, candidateToolName)) {
            return true;
        }
        return cartAdditionTool(toolName) && cartAdditionTool(candidateToolName);
    }

    private boolean cartAdditionTool(String name) {
        return "prepare_carts".equals(name) || "add_cart_line".equals(name);
    }
}
