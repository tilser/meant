package com.meant.api.plugin.transport.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "UCP Agent Profile", description = "Generated UCP agent capability profile")
public class AgentProfileController {

    private static final CacheControl PROFILE_CACHE_CONTROL = CacheControl.maxAge(Duration.ofMinutes(5))
            .cachePublic();

    private final AgentProfileProvider agentProfileProvider;

    @GetMapping(value = "/.well-known/ucp-agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get UCP agent profile",
            description = "Returns the immutable UCP agent profile generated from enabled plugins at startup."
    )
    @ApiResponse(
            responseCode = "200",
            description = "UCP agent profile",
            content = @Content(schema = @Schema(implementation = AgentProfile.class))
    )
    public ResponseEntity<AgentProfile> agentProfile() {
        return ResponseEntity.ok()
                .cacheControl(PROFILE_CACHE_CONTROL)
                .body(agentProfileProvider.profile());
    }
}
