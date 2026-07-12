package com.meant.api.plugin.transport.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ucp.diagnostics")
public record UcpMcpDiagnosticsProperties(
        boolean checkoutWireLoggingEnabled
) {
}
