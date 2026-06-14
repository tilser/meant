package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = UcpResourceReferenceDeserializer.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpResourceReference(
        String url
) {
}
