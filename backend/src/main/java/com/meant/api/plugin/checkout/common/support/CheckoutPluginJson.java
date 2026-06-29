package com.meant.api.plugin.checkout.common.support;

import com.meant.api.plugin.checkout.common.exception.UcpCheckoutResponseException;
import com.meant.api.plugin.spi.UcpToolResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class CheckoutPluginJson {

    private CheckoutPluginJson() {
    }

    public static <T> T parse(ObjectMapper objectMapper, UcpToolResponse response, Class<T> type) {
        try {
            if (response.textContent() != null && !response.textContent().isBlank()) {
                return objectMapper.readValue(response.textContent(), type);
            }
            if (response.structuredContent() != null) {
                return objectMapper.readValue(
                        objectMapper.writeValueAsString(response.structuredContent()),
                        type
                );
            }
            throw new UcpCheckoutResponseException("UCP checkout response did not contain text or structured content");
        } catch (JacksonException exception) {
            throw new UcpCheckoutResponseException("UCP checkout response could not be parsed as " + type.getSimpleName(),
                    exception);
        }
    }
}
