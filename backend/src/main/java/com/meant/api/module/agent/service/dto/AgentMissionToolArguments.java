package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AgentMissionToolArguments {

    private AgentMissionToolArguments() {
    }

    public record Create(
            @NotBlank @Size(max = 500) String goal,
            @Valid @Size(max = 20) List<AgentMissionDetails.Assumption> assumptions,
            @Valid @Size(max = 30) List<AgentMissionDetails.Requirement> requirements,
            @Valid AgentMissionDetails.Constraints constraints,
            @Valid @Size(max = 60) List<AgentMissionDetails.Alternative> alternatives
    ) {
    }

    public record Update(
            @NotNull UUID missionId,
            @Size(max = 500) String goal,
            ShoppingMissionStatus status,
            @Valid @Size(max = 20) List<AgentMissionDetails.Assumption> assumptions,
            @Valid @Size(max = 30) List<AgentMissionDetails.Requirement> requirements,
            @Valid AgentMissionDetails.Constraints constraints,
            @Valid @Size(max = 60) List<AgentMissionDetails.Alternative> alternatives
    ) {
    }

    public record EvaluateCoverage(
            @NotNull UUID missionId,
            @Valid @Size(max = 100) List<AgentMissionDetails.CoverageSelection> selections
    ) {
    }
}
