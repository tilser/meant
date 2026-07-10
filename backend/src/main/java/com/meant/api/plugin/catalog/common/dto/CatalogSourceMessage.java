package com.meant.api.plugin.catalog.common.dto;

import java.net.URI;

public record CatalogSourceMessage(
        String type,
        String code,
        String path,
        String content,
        String presentation,
        URI url
) {

    public CatalogSourceMessage {
        type = trimToNull(type);
        code = trimToNull(code);
        path = trimToNull(path);
        content = trimToNull(content);
        presentation = trimToNull(presentation);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
