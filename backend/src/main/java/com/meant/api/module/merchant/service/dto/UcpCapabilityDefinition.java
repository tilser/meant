package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpCapabilityDefinition(
        String id,
        String version,
        UcpResourceReference spec,
        UcpResourceReference schema,
        @JsonProperty("extends")
        @JsonDeserialize(using = UcpStringListDeserializer.class)
        List<String> extendsCapabilities,
        UcpCapabilityRequires requires
) {
}
