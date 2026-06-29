package com.meant.api.plugin.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

public record CapabilityId(@JsonValue String value) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public CapabilityId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static CapabilityId of(String value) {
        return new CapabilityId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
