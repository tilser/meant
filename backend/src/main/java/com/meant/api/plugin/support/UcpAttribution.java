package com.meant.api.plugin.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UcpAttribution(
        @JsonProperty("referring_domain")
        String referringDomain,
        @JsonProperty("utm_source")
        String utmSource,
        @JsonProperty("utm_medium")
        String utmMedium
) {
}
