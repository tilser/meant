package com.meant.api.plugin.catalog.common.support;

import com.meant.api.plugin.catalog.common.exception.UcpCatalogResponseException;
import com.meant.api.plugin.spi.UcpToolResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class CatalogPluginJson {

    private CatalogPluginJson() {
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
            throw new UcpCatalogResponseException("UCP catalog response did not contain text or structured content");
        } catch (JacksonException exception) {
            throw new UcpCatalogResponseException("UCP catalog response could not be parsed as " + type.getSimpleName(),
                    exception);
        }
    }
}
