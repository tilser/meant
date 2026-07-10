package com.meant.api.plugin.catalog.common.dto;

/**
 * An external identifier whose value remains case-sensitive because providers define their own
 * identity semantics. Namespace and value are trimmed, but their case is never folded.
 */
public record ExternalIdentifier(
        ExternalIdentifierType type,
        String namespace,
        String value
) {

    public ExternalIdentifier {
        if (type == null) {
            throw new IllegalArgumentException("External identifier type must not be null");
        }
        namespace = trimToNull(namespace);
        value = trimToNull(value);
        if (value == null) {
            throw new IllegalArgumentException("External identifier value must not be blank");
        }
    }

    public static ExternalIdentifier optional(
            ExternalIdentifierType type,
            String namespace,
            String value
    ) {
        return value == null || value.isBlank() ? null : new ExternalIdentifier(type, namespace, value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
