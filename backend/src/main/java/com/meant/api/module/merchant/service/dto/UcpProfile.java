package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpProfile(
        String version,
        @JsonProperty("supported_versions") Map<String, String> supportedVersions,
        @JsonDeserialize(contentUsing = UcpServiceDefinitionListDeserializer.class)
        Map<String, List<UcpServiceDefinition>> services,
        Map<String, List<UcpCapabilityDefinition>> capabilities,
        @JsonProperty("payment_handlers") Map<String, List<UcpPaymentHandlerDefinition>> paymentHandlers
) {
}
