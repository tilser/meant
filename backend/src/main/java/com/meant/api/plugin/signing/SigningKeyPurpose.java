package com.meant.api.plugin.signing;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SigningKeyPurpose {
    TRANSPORT("transport"),
    AP2_ISSUER("ap2_issuer"),
    SD_JWT_HOLDER("sd_jwt_holder");

    private final String value;

    SigningKeyPurpose(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
