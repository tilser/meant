package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpCapabilityRequires(
        UcpVersionRange protocol,
        Map<String, UcpVersionRange> capabilities
) {
}
