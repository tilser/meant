package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AgentMissionDetails(
        UUID missionId,
        UUID conversationId,
        String goal,
        ShoppingMissionStatus status,
        List<Assumption> assumptions,
        List<Requirement> requirements,
        Constraints constraints,
        List<Alternative> alternatives,
        List<Coverage> coverage,
        List<UUID> cartIds,
        List<UUID> checkoutAttemptIds,
        Instant createdAt,
        Instant updatedAt
) {

    public AgentMissionDetails {
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        coverage = coverage == null ? List.of() : List.copyOf(coverage);
        cartIds = cartIds == null ? List.of() : List.copyOf(cartIds);
        checkoutAttemptIds = checkoutAttemptIds == null ? List.of() : List.copyOf(checkoutAttemptIds);
    }

    public record Assumption(
            @NotBlank @Size(max = 80) String key,
            @NotBlank @Size(max = 300) String value
    ) {
    }

    public record Requirement(
            @NotBlank @Size(max = 80) String id,
            @NotBlank @Size(max = 200) String label,
            @Positive @Max(1000) Integer requiredQuantity,
            boolean optional,
            @Size(max = 8) List<@NotBlank @Size(max = 100) String> searchTerms
    ) {
        public Requirement {
            searchTerms = searchTerms == null ? List.of() : List.copyOf(searchTerms);
        }
    }

    public record Constraints(
            @Positive @Max(10_000) Integer partySize,
            @Size(max = 120) String occasion,
            @Size(max = 12) List<@NotBlank @Size(max = 100) String> dietaryRequirements,
            LocalDate neededBy,
            @Valid Budget budget
    ) {
        public Constraints {
            dietaryRequirements = dietaryRequirements == null ? List.of() : List.copyOf(dietaryRequirements);
        }
    }

    public record Budget(
            @PositiveOrZero Long amountMinor,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {
    }

    public record Alternative(
            @NotBlank @Size(max = 80) String requirementId,
            @NotBlank @Size(max = 200) String canonicalProductKey,
            @Size(max = 200) String offerKey,
            @Size(max = 200) String label,
            boolean selected
    ) {
    }

    public record CoverageSelection(
            @NotBlank @Size(max = 80) String requirementId,
            @Size(max = 200) String canonicalProductKey,
            @Size(max = 200) String offerKey,
            UUID inventoryItemId,
            @Positive @Max(1000) Integer quantity
    ) {
    }

    public enum CoverageState {
        MISSING,
        PARTIAL,
        COVERED,
        OPTIONAL
    }

    public record Coverage(
            String requirementId,
            CoverageState state,
            int requiredQuantity,
            int coveredQuantity,
            List<CoverageSelection> selections
    ) {
        public Coverage {
            selections = selections == null ? List.of() : List.copyOf(selections);
        }
    }
}
