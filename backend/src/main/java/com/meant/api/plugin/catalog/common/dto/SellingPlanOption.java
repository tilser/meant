package com.meant.api.plugin.catalog.common.dto;

public record SellingPlanOption(String name, String value) {

    public SellingPlanOption {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("Selling-plan option name and value must not be blank");
        }
        name = name.trim();
        value = value.trim();
    }
}
