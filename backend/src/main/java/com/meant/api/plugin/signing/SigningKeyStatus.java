package com.meant.api.plugin.signing;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SigningKeyStatus {
    ACTIVE("active"),
    RETIRING("retiring"),
    REVOKED("revoked");

    private final String value;

    SigningKeyStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
