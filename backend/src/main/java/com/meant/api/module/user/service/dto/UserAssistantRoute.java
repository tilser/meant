package com.meant.api.module.user.service.dto;

import java.util.Locale;

public record UserAssistantRoute(
        String action,
        String searchQuery,
        String clarifyingQuestion
) {

    public static final String ACTION_ANSWER = "answer";
    public static final String ACTION_SEARCH = "search_products";
    public static final String ACTION_CLARIFY = "clarify";

    public static UserAssistantRoute from(
            String action,
            String searchQuery,
            String clarifyingQuestion,
            String fallbackQuery
    ) {
        return new UserAssistantRoute(
                normalizeAction(action),
                textOrFallback(searchQuery, fallbackQuery),
                textOrFallback(clarifyingQuestion, "What kind of product or occasion should I focus on?")
        );
    }

    public static UserAssistantRoute fallback(String userMessage) {
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        if (asksAboutExistingContext(normalized)) {
            return new UserAssistantRoute(ACTION_ANSWER, userMessage, "");
        }
        boolean likelySearch = normalized.matches(".*\\b(find|search|recommend|show|buy|gift|under|cheaper|alternative|best)\\b.*");
        return new UserAssistantRoute(likelySearch ? ACTION_SEARCH : ACTION_ANSWER, userMessage, "");
    }

    public boolean isSearch() {
        return ACTION_SEARCH.equals(action);
    }

    public boolean isClarify() {
        return ACTION_CLARIFY.equals(action);
    }

    public UserAssistantRoute asAnswer() {
        return new UserAssistantRoute(ACTION_ANSWER, searchQuery, clarifyingQuestion);
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_ANSWER;
        }
        String normalized = action.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (ACTION_SEARCH.equals(normalized)
                || normalized.contains("search")) {
            return ACTION_SEARCH;
        }
        if (ACTION_CLARIFY.equals(normalized)
                || normalized.contains("clarif")) {
            return ACTION_CLARIFY;
        }
        if (ACTION_ANSWER.equals(normalized)
                || normalized.contains("answer")) {
            return ACTION_ANSWER;
        }
        return ACTION_ANSWER;
    }

    private static boolean asksAboutExistingContext(String normalized) {
        return normalized.matches(".*\\b(saved|cart|order|orders|account|profile|preference|preferences|visible|shown|current|already)\\b.*")
                || normalized.contains("products i have")
                || normalized.contains("products that i have")
                || normalized.contains("items i have")
                || normalized.contains("my products");
    }

    private static String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
