package com.meant.api.module.user.service.dto;

import java.util.List;

/** Evidence-backed user-facing personalization for one canonical product. */
public record UserCanonicalProductPersonalizationResult(
        String whyMeantForYou,
        List<String> matchedFilterIds,
        List<String> missedFilterIds,
        List<String> unknownFilterIds,
        List<String> hardConstraintFilterIds
) {

    private static final String SEARCH_RELEVANCE_TAKE =
            "This looks relevant to your search based on the available product details.";

    public UserCanonicalProductPersonalizationResult {
        whyMeantForYou = whyMeantForYou == null || whyMeantForYou.isBlank()
                ? SEARCH_RELEVANCE_TAKE
                : whyMeantForYou.trim();
        matchedFilterIds = matchedFilterIds == null ? List.of() : List.copyOf(matchedFilterIds);
        missedFilterIds = missedFilterIds == null ? List.of() : List.copyOf(missedFilterIds);
        unknownFilterIds = unknownFilterIds == null ? List.of() : List.copyOf(unknownFilterIds);
        hardConstraintFilterIds = hardConstraintFilterIds == null
                ? List.of()
                : List.copyOf(hardConstraintFilterIds);
    }

    public UserCanonicalProductPersonalizationResult(
            String whyMeantForYou,
            List<String> matchedFilterIds,
            List<String> missedFilterIds
    ) {
        this(whyMeantForYou, matchedFilterIds, missedFilterIds, List.of(), List.of());
    }

    public static UserCanonicalProductPersonalizationResult searchRelevance() {
        return new UserCanonicalProductPersonalizationResult(
                SEARCH_RELEVANCE_TAKE,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
