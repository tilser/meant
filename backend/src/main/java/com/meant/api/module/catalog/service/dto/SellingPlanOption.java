package com.meant.api.module.catalog.service.dto;

public record SellingPlanOption(String name, String value) {

    public SellingPlanOption {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("Selling-plan option name and value must not be blank");
        }
        name = name.trim();
        value = value.trim();
    }
}
