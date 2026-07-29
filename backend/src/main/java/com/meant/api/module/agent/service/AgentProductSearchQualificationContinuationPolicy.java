package com.meant.api.module.agent.service;

import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserProductSearchCategoryPolicy;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministically distinguishes an answer to a server question from a new shopping intent. */
@Component
public class AgentProductSearchQualificationContinuationPolicy {

    private static final Pattern CANCELLATION = Pattern.compile(
            "(?i)^\\s*(?:cancel|stop|never\\s*mind|nevermind|forget\\s+it|start\\s+over)\\s*[.!]?\\s*$");
    private static final Pattern EXPLICIT_REPLACEMENT_REQUEST = Pattern.compile(
            "(?i)\\b(?:actually\\s+)?(?:find|show|search(?:\\s+for)?|look\\s+for|"
                    + "looking\\s+for|shop\\s+for|buy|get\\s+me)\\b"
                    + "|\\b(?:instead|switch\\s+to|rather\\s+than)\\b"
    );
    private static final Pattern SIZE_ANSWER = Pattern.compile(
            "(?i)(?:\\bsize\\b|\\b(?:uk|us|eu)\\s*\\d{1,3}(?:\\.5)?\\b|"
                    + "\\b\\d{1,3}(?:\\.5)?\\b|"
                    + "^\\s*(?:x{0,4}[sl]|m|small|medium|large|"
                    + "(?:extra\\s+){1,4}(?:small|large)|one[- ]?size|os|osfa|"
                    + "\\d{1,3}(?:\\.5)?|w\\d{1,3}(?:\\s*l\\d{1,3})?)\\s*$)"
    );
    private static final Pattern CLEAR_FOOTWEAR_SIZE_ANSWER = Pattern.compile(
            "(?i)^\\s*(?:(?:my|the)\\s+)?(?:(?:uk|us|eu)\\s+)?"
                    + "(?:shoe|shoes|sneaker|sneakers|trainer|trainers|boot|boots|"
                    + "sandal|sandals|footwear)\\s+size\\s*(?:is\\s+)?"
                    + "(?:(?:uk|us|eu)\\s*)?(?:x{0,4}[sl]|m|small|medium|large|"
                    + "one[- ]?size|os|osfa|\\d{1,3}(?:\\.5)?)\\s*[.!]?\\s*$"
    );
    private static final Pattern INDIFFERENCE = Pattern.compile(
            "(?i)\\b(?:any|either|neither|none|no\\s+preference|doesn['’]?t\\s+matter|"
                    + "don['’]?t\\s+care|do\\s+not\\s+care|whatever)\\b"
    );
    private static final Pattern BARE_MULTIWORD_LOCATION = Pattern.compile(
            "(?iu)^\\s*(?:san|santa|saint|st\\.?|new|los|las|fort|mount|north|south|east|west)"
                    + "\\s+[\\p{L}][\\p{L}'’.-]*(?:\\s+[\\p{L}][\\p{L}'’.-]*){0,2}\\s*$"
    );
    private static final Pattern PROPER_NOUN_LOCATION = Pattern.compile(
            "^\\s*\\p{Lu}[\\p{L}'’.-]*(?:\\s+(?:(?:de|del|la|las|los|da|do|dos|van|von)"
                    + "|\\p{Lu}[\\p{L}'’.-]*)){1,4}\\s*$"
    );
    private static final Pattern CLEAR_PASS_THROUGH_COMMAND = Pattern.compile(
            "(?iu)^\\s*(?:tell\\s+me|explain|summarize|write|draft|translate|calculate|"
                    + "say|sing|play|help\\s+me\\s+with)\\b"
    );
    private static final Pattern CLEAR_PASS_THROUGH_TOPIC = Pattern.compile(
            "(?iu)\\b(?:joke|weather|return\\s+policy|refund\\s+policy|privacy\\s+policy|"
                    + "terms\\s+(?:and|of)|order\\s+status|customer\\s+service|password)\\b"
    );
    private static final Pattern GREETING_OR_ACKNOWLEDGEMENT = Pattern.compile(
            "(?iu)^\\s*(?:hi|hello|hey|thanks|thank\\s+you|good\\s+(?:morning|afternoon|evening))"
                    + "\\s*[.!]?\\s*$"
    );
    private static final Pattern PLAUSIBLE_OPAQUE_ANSWER = Pattern.compile(
            "(?iu)^[\\p{L}\\p{N}'’.,/&+()\\-\\s]{1,80}$"
    );
    private static final Pattern SENTENCE_LIKE_OPENER = Pattern.compile(
            "(?iu)^\\s*(?:i|we|you|he|she|they|it)\\s+"
                    + "(?:want|need|think|wonder|asked|said|mean|remember|have\\s+a\\s+question)\\b"
    );
    private static final Pattern FOLLOW_ON_ACTION = Pattern.compile(
            "(?iu)(?:[,;]\\s*|\\s+(?:and(?:\\s+then)?|then)\\s+)"
                    + "(?:please\\s+)?(?:add|put|place|buy|purchase|order|pin|save|remove|delete|"
                    + "checkout|check\\s+out|build\\s+(?:a\\s+)?cart|"
                    + "compare|pick|choose|select|inspect|review|read|show|tell|get|find)\\b"
    );

    private final UserProductSearchCategoryPolicy categoryPolicy;

    public AgentProductSearchQualificationContinuationPolicy(
            UserProductSearchCategoryPolicy categoryPolicy
    ) {
        this.categoryPolicy = categoryPolicy;
    }

    public Decision decide(UserProductSearchQualificationSnapshot pending, String currentTurn) {
        String turn = currentTurn == null ? "" : currentTurn.trim();
        String answer = answerClause(turn);
        if (CANCELLATION.matcher(turn).matches()) {
            return Decision.CANCEL;
        }
        var missingTargets = pending.plan().missingTargets();
        var originalCategory = categoryPolicy.category(pending.originalQuery(), pending.originalQuery());
        if (originalCategory == UserProductSearchCategoryPolicy.Category.FOOTWEAR
                && missingTargets.contains(UserProductSearchQuestionTarget.SIZE)
                && CLEAR_FOOTWEAR_SIZE_ANSWER.matcher(answer).matches()) {
            return Decision.ANSWER;
        }
        var currentCategory = categoryPolicy.category("", answer);
        if (missingTargets.contains(UserProductSearchQuestionTarget.SHIPS_TO)
                && plausibleBareDestination(answer, currentCategory)) {
            return Decision.ANSWER;
        }
        if (missingTargets.isEmpty()) {
            return Decision.NEW_INTENT;
        }
        if (clearlyPassesThrough(answer, missingTargets)) {
            return Decision.PASS_THROUGH;
        }
        if (currentCategory != UserProductSearchCategoryPolicy.Category.OTHER
                && currentCategory != originalCategory) {
            return Decision.NEW_INTENT;
        }
        if (EXPLICIT_REPLACEMENT_REQUEST.matcher(answer).find()) {
            return Decision.NEW_INTENT;
        }
        var currentSubject = categoryPolicy.productSubject(answer);
        if (currentCategory != UserProductSearchCategoryPolicy.Category.OTHER
                && currentSubject.map(subject -> subject.terms().size() >= 2).orElse(false)) {
            return Decision.NEW_INTENT;
        }
        if (missingTargets.stream().anyMatch(target -> mentionsAnswer(target, answer))) {
            return Decision.ANSWER;
        }
        if (productSubjectChanged(pending.originalQuery(), answer, currentCategory)) {
            return Decision.NEW_INTENT;
        }
        /*
         * Supported values such as a city, an uncommon color, or a regional size can be opaque
         * free text. Keep short value-like replies attached to the persisted question, but let
         * sentences and unrelated requests reach the general agent without cancelling that state.
         */
        return plausibleOpaqueAnswer(answer) ? Decision.ANSWER : Decision.PASS_THROUGH;
    }

    private String answerClause(String turn) {
        var matcher = FOLLOW_ON_ACTION.matcher(turn);
        return matcher.find() ? turn.substring(0, matcher.start()).trim() : turn;
    }

    private boolean clearlyPassesThrough(
            String turn,
            java.util.List<UserProductSearchQuestionTarget> missingTargets
    ) {
        if (turn.contains("?")
                || GREETING_OR_ACKNOWLEDGEMENT.matcher(turn).matches()
                || CLEAR_PASS_THROUGH_TOPIC.matcher(turn).find()) {
            return true;
        }
        return CLEAR_PASS_THROUGH_COMMAND.matcher(turn).find()
                && missingTargets.stream().noneMatch(target -> mentionsAnswer(target, turn));
    }

    private boolean plausibleOpaqueAnswer(String turn) {
        if (!PLAUSIBLE_OPAQUE_ANSWER.matcher(turn).matches()
                || SENTENCE_LIKE_OPENER.matcher(turn).find()) {
            return false;
        }
        return turn.trim().split("\\s+").length <= 6;
    }

    private boolean plausibleBareDestination(
            String turn,
            UserProductSearchCategoryPolicy.Category currentCategory
    ) {
        if (turn.isBlank()
                || currentCategory != UserProductSearchCategoryPolicy.Category.OTHER
                || EXPLICIT_REPLACEMENT_REQUEST.matcher(turn).find()) {
            return false;
        }
        String normalized = turn.trim().replaceAll("[.!?]+$", "");
        if (BARE_MULTIWORD_LOCATION.matcher(normalized).matches()
                || PROPER_NOUN_LOCATION.matcher(normalized).matches()) {
            return true;
        }
        if (java.util.Set.of(
                "USA",
                "UNITED STATES OF AMERICA",
                "UK",
                "GREAT BRITAIN",
                "CZECH REPUBLIC"
        ).contains(normalized.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return java.util.Arrays.stream(Locale.getISOCountries())
                .map(country -> new Locale.Builder()
                        .setRegion(country)
                        .build()
                        .getDisplayCountry(Locale.ENGLISH))
                .anyMatch(country -> country.equalsIgnoreCase(normalized));
    }

    private boolean productSubjectChanged(
            String originalQuery,
            String currentTurn,
            UserProductSearchCategoryPolicy.Category currentCategory
    ) {
        var originalSubject = categoryPolicy.productSubject(originalQuery);
        var currentSubject = categoryPolicy.productSubject(currentTurn);
        if (originalSubject.isEmpty()
                || currentSubject.isEmpty()
                || originalSubject.get().head().equals(currentSubject.get().head())) {
            return false;
        }
        return currentCategory != UserProductSearchCategoryPolicy.Category.OTHER
                || EXPLICIT_REPLACEMENT_REQUEST.matcher(currentTurn).find()
                || currentSubject.get().terms().size() >= 2;
    }

    private boolean mentionsAnswer(UserProductSearchQuestionTarget target, String turn) {
        if (INDIFFERENCE.matcher(turn).find()) {
            return true;
        }
        String normalized = turn.toLowerCase(Locale.ROOT);
        return switch (target) {
            case CONDITION -> containsAny(normalized, "condition", "new", "used", "secondhand", "preowned");
            case SHIPS_TO -> containsAny(
                    normalized,
                    "ship to",
                    "shipping",
                    "deliver",
                    "delivery",
                    "destination",
                    "location",
                    "country",
                    "postal"
            );
            case SHIPS_FROM -> containsAny(normalized, "ship from", "shipping origin", "made in", "origin");
            case PRICE -> containsAny(
                    normalized, "price", "budget", "under", "below", "over", "above", "usd", "$");
            case COLOR -> containsAny(
                    normalized, "color", "colour", "black", "white", "red", "blue", "green", "brown",
                    "grey", "gray", "pink", "purple", "orange", "yellow", "beige", "navy");
            case SIZE -> SIZE_ANSWER.matcher(turn).find();
            case TARGET_GENDER -> containsAny(
                    normalized, "gender", "men", "women", "male", "female", "unisex", "boy", "girl");
            case RATING -> containsAny(normalized, "rating", "rated", "star", "review");
            case PRICE_TIER -> containsAny(normalized, "price tier", "cheap", "premium", "luxury");
        };
    }

    private boolean containsAny(String value, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(value::contains);
    }

    public enum Decision {
        ANSWER,
        NEW_INTENT,
        CANCEL,
        PASS_THROUGH
    }
}
