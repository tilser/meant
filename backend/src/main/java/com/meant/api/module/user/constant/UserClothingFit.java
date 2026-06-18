package com.meant.api.module.user.constant;

import java.util.Arrays;
import java.util.Optional;

public enum UserClothingFit {
    MEN("men", "men's sizing"),
    WOMEN("women", "women's sizing"),
    OTHER("other", "other fit preference");

    private final String value;
    private final String label;

    UserClothingFit(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static Optional<UserClothingFit> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(fit -> fit.value.equals(normalized))
                .findFirst();
    }

    public static String persistedValue(String value) {
        if (value == null || "none".equals(value.trim())) {
            return null;
        }
        return fromValue(value)
                .map(UserClothingFit::value)
                .orElse(null);
    }

    public static String labelFor(String value) {
        return fromValue(value)
                .map(UserClothingFit::label)
                .orElse(null);
    }
}
