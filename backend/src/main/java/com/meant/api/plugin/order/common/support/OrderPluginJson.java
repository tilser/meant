package com.meant.api.plugin.order.common.support;

import com.meant.api.plugin.order.common.exception.UcpOrderResponseException;
import com.meant.api.plugin.spi.UcpToolResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class OrderPluginJson {

    private OrderPluginJson() {
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
            throw new UcpOrderResponseException("UCP order response did not contain text or structured content");
        } catch (JacksonException exception) {
            throw new UcpOrderResponseException("UCP order response could not be parsed as " + type.getSimpleName(),
                    exception);
        }
    }
}
