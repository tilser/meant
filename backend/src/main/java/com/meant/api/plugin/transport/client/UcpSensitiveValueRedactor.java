package com.meant.api.plugin.transport.client;

import java.util.regex.Pattern;

final class UcpSensitiveValueRedactor {

    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)((?:\\\\?\")?(?:authorization|client[_-]?secret|access[_-]?token|ec[_-]?auth)"
                    + "(?:\\\\?\")?\\s*[:=]\\s*(?:\\\\?\")?)[^\\s,\"}\\\\]+"
    );
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]*)?\\b"
    );

    private UcpSensitiveValueRedactor() {
    }

    static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = BEARER_TOKEN.matcher(value).replaceAll("$1[redacted]");
        redacted = SENSITIVE_FIELD.matcher(redacted).replaceAll("$1[redacted]");
        return JWT.matcher(redacted).replaceAll("[redacted-jwt]");
    }
}
