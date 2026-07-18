package com.meant.api.module.agent.controller;

import com.meant.api.module.agent.controller.response.AgentRunResponse;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.command.CancelAgentRunCommand;
import com.meant.api.module.agent.service.query.GetAgentRunQuery;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/users/me/agent/runs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Commerce agent", description = "Durable commerce-agent run state and event replay")
public class AgentRunController {

    private final AgentRunService runService;
    private final AgentEventStreamWriter eventStreamWriter;

    @GetMapping("/{runId}")
    @Operation(summary = "Get a durable agent run snapshot")
    public AgentRunResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        return AgentRunResponse.from(runService.get(new GetAgentRunQuery(userId, runId)));
    }

    @PostMapping("/{runId}/cancel")
    @Operation(summary = "Request cancellation of an active or queued run")
    public AgentRunResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        return AgentRunResponse.from(runService.requestCancellation(new CancelAgentRunCommand(userId, runId)));
    }

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Replay and follow run events",
            description = "Resumes after the query cursor or Last-Event-ID. Disconnecting does not cancel the run."
    )
    public SseEmitter events(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @RequestParam(required = false) Long afterCursor,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        return eventStreamWriter.open(userId, runId, resumeCursor(afterCursor, lastEventId));
    }

    private long resumeCursor(Long afterCursor, String lastEventId) {
        if (lastEventId != null && !lastEventId.isBlank()) {
            long parsed = Long.parseLong(lastEventId);
            if (parsed < 0) {
                throw new IllegalArgumentException("Last-Event-ID cannot be negative.");
            }
            return parsed;
        }
        if (afterCursor == null) {
            return 0L;
        }
        if (afterCursor < 0) {
            throw new IllegalArgumentException("afterCursor cannot be negative.");
        }
        return afterCursor;
    }
}
