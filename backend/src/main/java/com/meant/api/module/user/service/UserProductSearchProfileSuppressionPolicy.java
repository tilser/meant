package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Removes profile-derived signals that conflict with buyer-grounded request decisions. */
@Component
public class UserProductSearchProfileSuppressionPolicy {

    private static final Set<String> SIZE_FILTER_IDS =
            Set.of("plus-size-available", "petite-available", "tall-available");
    private static final Set<String> RATING_FILTER_IDS =
            Set.of("highly-rated", "many-reviews");
    private static final Set<String> PRICE_FILTER_IDS =
            Set.of("best-value", "budget-friendly", "premium-quality");
    private static final Set<String> CONDITION_FILTER_IDS =
            Set.of("secondhand-or-refurbished");
    private static final Set<String> SHIPS_FROM_FILTER_IDS =
            Set.of("locally-made", "made-in-usa");
    private static final Set<UserTasteSignalType> SEMANTIC_SIGNAL_TYPES =
            Set.of(UserTasteSignalType.FILTER, UserTasteSignalType.CATEGORY);
    private static final Pattern COLOR_LABEL = Pattern.compile(
            "(?:^|\\b)(?:colou?rs?|black|white|gr[ae]y|red|blue|green|brown|pink|"
                    + "purple|orange|yellow|beige|navy|teal|gold|silver)(?:\\b|$)"
    );
    private static final Pattern ARTIFICIAL_COLOR_LABEL =
            Pattern.compile("(?:^|\\b)artificial[ -]?colou?rs?(?:\\b|$)");
    private static final Pattern SIZE_LABEL = Pattern.compile(
            "(?:^|\\b)(?:size|sizes|sizing|petite|plus[ -]?size|"
                    + "tall[ -]?(?:available|size|sizes|sizing|fit))(?:\\b|$)"
    );
    private static final Pattern STANDALONE_SIZE_LABEL = Pattern.compile(
            "^(?:xxs|xs|s|m|l|xl|xxl|xxxl|(?:eu|us|uk)?[ -]?\\d{1,3}(?:\\.\\d)?)$"
    );
    private static final Pattern GENDERED_LABEL = Pattern.compile(
            "(?:^|\\b)(?:man|men|mens|woman|women|womens|male|female|unisex|"
                    + "boys?|girls?|gendered?)(?:\\b|$)"
    );
    private static final Pattern CONDITION_LABEL = Pattern.compile(
            "(?:^|\\b)(?:condition|brand[ -]?new|new|used|second[ -]?hand|"
                    + "pre[ -]?owned|refurbished|renewed)(?:\\b|$)"
    );
    private static final Pattern SHIPS_TO_LABEL = Pattern.compile(
            "(?:^|\\b)(?:ships?|shipping|delivers?|delivery|delivered)[ -]?(?:to|destination)"
                    + "(?:\\b|$)"
    );
    private static final Pattern SHIPS_FROM_LABEL = Pattern.compile(
            "(?:^|\\b)(?:locally[ -]?made|made[ -]?in|ships?[ -]?from|"
                    + "shipping[ -]?from|country[ -]?of[ -]?origin|origin)(?:\\b|$)"
    );
    private static final Pattern RATING_LABEL = Pattern.compile(
            "(?:^|\\b)(?:ratings?|rated|reviews?|stars?|customer[ -]?feedback)(?:\\b|$)"
    );
    private static final Pattern PRICE_LABEL = Pattern.compile(
            "(?:^|\\b)(?:price|priced|pricing|budget|budget[ -]?friendly|best[ -]?value|"
                    + "value|affordable|cheap|low[ -]?cost|premium|premium[ -]?quality|"
                    + "luxury|expensive)"
                    + "(?:\\b|$)"
    );

    public UserSettingsResult settings(
            UserSettingsResult settings,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        Set<UserProductSearchQuestionTarget> targets = safeTargets(profileSuppressionTargets);
        if (settings == null || targets.isEmpty()) {
            return settings;
        }
        List<ShoppingFilterResult> filters = safe(settings.filters()).stream()
                .filter(filter -> !suppressed(filter, targets))
                .toList();
        boolean suppressPrice = targets.contains(UserProductSearchQuestionTarget.PRICE)
                || targets.contains(UserProductSearchQuestionTarget.PRICE_TIER);
        boolean suppressDestination = targets.contains(UserProductSearchQuestionTarget.SHIPS_TO);
        return new UserSettingsResult(
                suppressPrice ? null : settings.budget(),
                settings.currency(),
                targets.contains(UserProductSearchQuestionTarget.TARGET_GENDER)
                        ? null
                        : settings.clothingFit(),
                suppressDestination ? null : settings.location(),
                suppressDestination ? List.of() : settings.locations(),
                filters,
                settings.availableFilters(),
                settings.parsedFilterIds(),
                settings.unmappedPreferences(),
                settings.createdAt(),
                settings.updatedAt()
        );
    }

    public UserTasteProfileResult tasteProfile(
            UserTasteProfileResult tasteProfile,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        Set<UserProductSearchQuestionTarget> targets = safeTargets(profileSuppressionTargets);
        if (tasteProfile == null || targets.isEmpty()) {
            return tasteProfile;
        }
        List<UserTasteSignalResult> signals = safe(tasteProfile.signals()).stream()
                .filter(signal -> !suppressed(signal, targets))
                .toList();
        String targetIdentity = targets.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
        String profileHash =
                value(tasteProfile.profileHash()) + ":profileSuppression=" + targetIdentity;
        return new UserTasteProfileResult(profileHash, signals, tasteProfile.suggestions());
    }

    public boolean suppressed(
            ShoppingFilterResult filter,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        if (filter == null) {
            return false;
        }
        Set<UserProductSearchQuestionTarget> targets = safeTargets(profileSuppressionTargets);
        String identity = normalized(filter.id());
        /*
         * Saved settings use a finite server-owned filter catalog. Matching their prose would,
         * for example, confuse food's "avoid artificial colors" with a garment color choice.
         */
        return suppressesKnownIdentity(identity, targets);
    }

    public boolean suppressed(
            UserTasteSignalResult signal,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        if (signal == null) {
            return false;
        }
        Set<UserProductSearchQuestionTarget> targets = safeTargets(profileSuppressionTargets);
        if (targets.isEmpty()) {
            return false;
        }
        /*
         * QUERY rows contain complete historical searches, not typed dimensions. Once a buyer
         * overrides any profile dimension, no historical query can be proven neutral for that
         * request (for example "running shoes 46" or a saved destination).
         */
        if (signal.signalType() == UserTasteSignalType.QUERY) {
            return true;
        }
        if (suppressesKnownIdentity(normalized(signal.suggestedFilterId()), targets)
                || suppressesKnownIdentity(normalized(signal.signalKey()), targets)) {
            return true;
        }
        if (!SEMANTIC_SIGNAL_TYPES.contains(signal.signalType())) {
            return false;
        }
        String label = joined(signal.signalKey(), signal.label());
        return suppressesSemanticLabel(label, targets);
    }

    private boolean suppressesKnownIdentity(
            String identity,
            Set<UserProductSearchQuestionTarget> targets
    ) {
        if (identity == null) {
            return false;
        }
        if (targets.contains(UserProductSearchQuestionTarget.SIZE)
                && SIZE_FILTER_IDS.contains(identity)) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.RATING)
                && RATING_FILTER_IDS.contains(identity)) {
            return true;
        }
        if ((targets.contains(UserProductSearchQuestionTarget.PRICE)
                || targets.contains(UserProductSearchQuestionTarget.PRICE_TIER))
                && PRICE_FILTER_IDS.contains(identity)) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.CONDITION)
                && CONDITION_FILTER_IDS.contains(identity)) {
            return true;
        }
        return targets.contains(UserProductSearchQuestionTarget.SHIPS_FROM)
                && SHIPS_FROM_FILTER_IDS.contains(identity);
    }

    private boolean suppressesSemanticLabel(
            String label,
            Set<UserProductSearchQuestionTarget> targets
    ) {
        if (label == null) {
            return false;
        }
        if (targets.contains(UserProductSearchQuestionTarget.COLOR)
                && COLOR_LABEL.matcher(label).find()
                && !ARTIFICIAL_COLOR_LABEL.matcher(label).find()) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.SIZE)
                && (SIZE_LABEL.matcher(label).find()
                || STANDALONE_SIZE_LABEL.matcher(label).matches())) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.TARGET_GENDER)
                && GENDERED_LABEL.matcher(label).find()) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.CONDITION)
                && CONDITION_LABEL.matcher(label).find()) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.SHIPS_TO)
                && SHIPS_TO_LABEL.matcher(label).find()) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.SHIPS_FROM)
                && SHIPS_FROM_LABEL.matcher(label).find()) {
            return true;
        }
        if (targets.contains(UserProductSearchQuestionTarget.RATING)
                && RATING_LABEL.matcher(label).find()) {
            return true;
        }
        return (targets.contains(UserProductSearchQuestionTarget.PRICE)
                || targets.contains(UserProductSearchQuestionTarget.PRICE_TIER))
                && PRICE_LABEL.matcher(label).find();
    }

    private Set<UserProductSearchQuestionTarget> safeTargets(
            Set<UserProductSearchQuestionTarget> targets
    ) {
        return targets == null ? Set.of() : targets;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String joined(String... values) {
        return java.util.Arrays.stream(values)
                .map(this::normalized)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
