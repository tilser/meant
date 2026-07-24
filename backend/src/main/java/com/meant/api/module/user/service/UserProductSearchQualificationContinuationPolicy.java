package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministically distinguishes an answer to a server question from a new shopping intent. */
@Component
public class UserProductSearchQualificationContinuationPolicy {

    private static final Pattern CANCELLATION = Pattern.compile(
            "(?i)^\\s*(?:cancel|stop|never\\s*mind|nevermind|forget\\s+it|start\\s+over)\\s*[.!]?\\s*$");
    private static final Pattern SIZE = Pattern.compile(
            "(?i)(?:\\bsize\\s*[a-z0-9./-]+\\b|\\b(?:uk|us|eu)\\s*\\d{1,2}(?:\\.5)?\\b)");
    private static final Pattern DESTINATION = Pattern.compile(
            "(?i)(?:\\b(?:ship|deliver|send)(?:ped|ed|ing|s)?\\s+(?:it\\s+)?to\\b"
                    + "|\\b\\d{4,10}(?:[- ]\\d{3,4})?\\b)");
    private static final Pattern ORIGIN = Pattern.compile(
            "(?i)(?:\\b(?:ship(?:ped|ping|s)?\\s+from|made\\s+in|origin(?:ating)?\\s+from)\\b)");
    private static final Pattern PRICE = Pattern.compile(
            "(?i)(?:[$€£]\\s*\\d|\\b(?:usd|eur|gbp|price|budget|under|below|over|above|between)\\b)");
    private static final Pattern RATING = Pattern.compile(
            "(?i)(?:\\b\\d(?:\\.\\d)?\\s*(?:stars?|rating)\\b|\\b(?:rated?|reviews?)\\b)");
    private static final Pattern COLOR = Pattern.compile(
            "(?i)\\b(?:color|colour|black|white|red|blue|green|brown|grey|gray|pink|purple|"
                    + "orange|yellow|beige|navy|teal|gold|silver)\\b");
    private static final Pattern GENDER = Pattern.compile(
            "(?i)\\b(?:men'?s?|women'?s?|male|female|unisex|boys?|girls?|target\\s+gender)\\b");
    private static final Pattern CONDITION = Pattern.compile(
            "(?i)\\b(?:new|used|secondhand|second\\s+hand|preowned|pre\\s+owned|condition)\\b");
    private static final Pattern PRICE_TIER = Pattern.compile(
            "(?i)\\b(?:(?:low|medium|high)\\s+price\\s+tier|price\\s+tier|budget|premium|luxury)\\b");
    private static final Pattern INDIFFERENCE = Pattern.compile(
            "(?i)\\b(?:any|either|no\\s+preference|doesn'?t\\s+matter|do\\s+not\\s+care)\\b");

    private final UserProductSearchCategoryPolicy categoryPolicy;

    public UserProductSearchQualificationContinuationPolicy(
            UserProductSearchCategoryPolicy categoryPolicy
    ) {
        this.categoryPolicy = categoryPolicy;
    }

    public Decision decide(UserProductSearchQualificationSnapshot pending, String currentTurn) {
        String turn = currentTurn == null ? "" : currentTurn.trim();
        if (CANCELLATION.matcher(turn).matches()) {
            return Decision.CANCEL;
        }
        var originalCategory = categoryPolicy.category(pending.originalQuery(), pending.originalQuery());
        var currentCategory = categoryPolicy.category("", turn);
        if (currentCategory != UserProductSearchCategoryPolicy.Category.OTHER
                && currentCategory != originalCategory) {
            return Decision.NEW_INTENT;
        }
        List<UserProductSearchQuestionTarget> targets = pending.plan().missingTargets();
        if (targets.isEmpty()) {
            return Decision.NEW_INTENT;
        }
        long matches = targets.stream().filter(target -> matches(target, turn, targets.size())).count();
        return matches > 0 ? Decision.ANSWER : Decision.NEW_INTENT;
    }

    private boolean matches(UserProductSearchQuestionTarget target, String value, int targetCount) {
        if (INDIFFERENCE.matcher(value).find()
                && (targetCount == 1 || value.toLowerCase(Locale.ROOT).contains("all"))) {
            return true;
        }
        return switch (target) {
            case CONDITION -> CONDITION.matcher(value).find();
            case SHIPS_TO -> DESTINATION.matcher(value).find()
                    || containsCountryCode(value)
                    || containsCountryName(value);
            case SHIPS_FROM -> ORIGIN.matcher(value).find()
                    || containsCountryCode(value)
                    || containsCountryName(value);
            case PRICE -> PRICE.matcher(value).find();
            case COLOR -> COLOR.matcher(value).find();
            case SIZE -> SIZE.matcher(value).find()
                    || targetCount == 1 && value.matches("(?i)\\s*[a-z]?\\d{1,3}(?:\\.5)?[a-z]?\\s*");
            case TARGET_GENDER -> GENDER.matcher(value).find();
            case RATING -> RATING.matcher(value).find();
            case PRICE_TIER -> PRICE_TIER.matcher(value).find();
        };
    }

    private boolean containsCountryCode(String value) {
        java.util.regex.Matcher matcher = Pattern.compile("(?<![\\p{L}\\p{N}])[A-Z]{2}(?![\\p{L}\\p{N}])")
                .matcher(value);
        while (matcher.find()) {
            if (CountryCodeNormalizer.normalizeAlpha2(matcher.group()) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCountryName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.equals("usa") || normalized.equals("uk")
                || normalized.equals("united states") || normalized.equals("great britain")) {
            return true;
        }
        return java.util.Arrays.stream(Locale.getISOCountries())
                .map(code -> new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH))
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    public enum Decision {
        ANSWER,
        NEW_INTENT,
        CANCEL
    }
}
