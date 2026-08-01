package com.meant.api.plugin.transport.profile;

import com.meant.api.plugin.support.UcpAttribution;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ucp.agent.attribution")
public record AgentAttributionProperties(
        @NotBlank String referringDomain,
        @NotBlank String utmSource,
        @NotBlank String utmMedium
) {

    public UcpAttribution attribution() {
        return new UcpAttribution(referringDomain, utmSource, utmMedium);
    }
}
