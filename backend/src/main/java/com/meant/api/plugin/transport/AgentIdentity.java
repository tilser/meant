package com.meant.api.plugin.transport;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ucp.agent")
public record AgentIdentity(
        @NotNull URI profileUrl,
        @NotBlank String protocolVersion,
        @NotBlank String signingKeyId
) {

    @AssertTrue(message = "profileUrl must be absolute")
    public boolean hasAbsoluteProfileUrl() {
        return profileUrl != null && profileUrl.isAbsolute();
    }
}
