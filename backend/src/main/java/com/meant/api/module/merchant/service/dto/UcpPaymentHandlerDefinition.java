package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpPaymentHandlerDefinition(
        String id,
        String version,
        UcpResourceReference spec,
        UcpResourceReference schema,
        JsonNode config
) {

    public UcpPaymentHandlerDefinition(
            String id,
            String version,
            UcpResourceReference spec,
            UcpResourceReference schema
    ) {
        this(id, version, spec, schema, null);
    }
}
