package com.meant.api.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Service health checks")
public class HealthController {

    @GetMapping
    @Operation(summary = "Get API health", description = "Returns a lightweight health response for the Meant API.")
    @ApiResponse(
            responseCode = "200",
            description = "API health",
            content = @Content(schema = @Schema(implementation = HealthResponse.class))
    )
    public HealthResponse health() {
        return new HealthResponse("Meant API", "ok", Instant.now());
    }

    @Schema(name = "HealthResponse", description = "Current API health status.")
    public record HealthResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Service name.", example = "Meant API")
            String service,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Service status.", example = "ok")
            String status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Response timestamp.")
            Instant timestamp
    ) {
    }
}
