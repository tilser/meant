package com.meant.api.module.agent.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AgentCartToolArguments {

    private AgentCartToolArguments() {
    }

    public record GetActive(@Min(1) @Max(20) Integer limit) {
    }

    public record Get(@NotNull UUID cartId, Boolean refresh) {
    }

    public record Prepare(
            @NotEmpty @Size(max = 50) List<@NotNull @Valid ExactOffer> offers
    ) {
    }

    public record ExactOffer(
            @NotBlank @Size(max = 200) String offerKey,
            @Positive @Max(1000) Integer quantity
    ) {
    }

    public record AddLine(
            @NotNull UUID cartId,
            @NotBlank @Size(max = 200) String offerKey,
            @Positive @Max(1000) Integer quantity
    ) {
    }

    public record UpdateLine(
            @NotNull UUID cartId,
            @NotNull UUID cartLineId,
            @Positive @Max(1000) Integer quantity
    ) {
    }

    public record RemoveLine(
            @NotNull UUID cartId,
            @NotNull UUID cartLineId
    ) {
    }
}
