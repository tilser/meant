package com.meant.api.module.user.constant;

import java.util.List;

public final class ShoppingFilterDefaults {

    public static final List<String> DEFAULT_ACTIVE_FILTER_IDS = List.of(
            "organic",
            "natural-materials",
            "no-polyester",
            "highly-rated",
            "sustainable-brands",
            "best-value",
            "low-sugar"
    );

    private ShoppingFilterDefaults() {
    }
}
