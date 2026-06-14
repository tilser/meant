package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpPaymentHandlerDefinition(
        String id,
        String version,
        UcpResourceReference spec,
        UcpResourceReference schema
) {
}
