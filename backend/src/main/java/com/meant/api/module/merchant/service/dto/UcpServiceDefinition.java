package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpServiceDefinition(
        String id,
        String version,
        UcpResourceReference spec,
        String transport,
        String endpoint,
        UcpResourceReference schema
) {
}
