package com.meant.api.plugin.cart.common.support;

import com.meant.api.plugin.cart.common.exception.UcpCartResponseException;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartRootResponse;
import com.meant.api.plugin.spi.UcpToolResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class CartPluginJson {

    private CartPluginJson() {
    }

    public static <T> T parse(ObjectMapper objectMapper, UcpToolResponse response, Class<T> type) {
        try {
            return objectMapper.readValue(responsePayload(objectMapper, response), type);
        } catch (JacksonException exception) {
            throw new UcpCartResponseException("UCP cart response could not be parsed as " + type.getSimpleName(),
                    exception);
        }
    }

    public static UcpCartResponse parseCartResponse(ObjectMapper objectMapper, UcpToolResponse response) {
        String payload = responsePayload(objectMapper, response);
        try {
            UcpCartResponse cartResponse = objectMapper.readValue(payload, UcpCartResponse.class);
            if (cartResponse != null && cartResponse.cart() != null) {
                return cartResponse;
            }
            UcpCartRootResponse rootResponse = objectMapper.readValue(payload, UcpCartRootResponse.class);
            return rootResponse == null || !rootResponse.hasCart()
                    ? cartResponse
                    : rootResponse.toUcpCartResponse();
        } catch (JacksonException exception) {
            throw new UcpCartResponseException("UCP cart response could not be parsed as UcpCartResponse",
                    exception);
        }
    }

    private static String responsePayload(ObjectMapper objectMapper, UcpToolResponse response) {
        try {
            if (response.textContent() != null && !response.textContent().isBlank()) {
                return response.textContent();
            }
            if (response.structuredContent() != null) {
                return objectMapper.writeValueAsString(response.structuredContent());
            }
            throw new UcpCartResponseException("UCP cart response did not contain text or structured content");
        } catch (JacksonException exception) {
            throw new UcpCartResponseException("UCP cart response could not be serialized", exception);
        }
    }
}
