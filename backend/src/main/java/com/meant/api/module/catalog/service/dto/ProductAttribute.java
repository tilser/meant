package com.meant.api.module.catalog.service.dto;

public record ProductAttribute(String group, String name, String value) {

    public ProductAttribute {
        group = trimToNull(group);
        name = trimToNull(name);
        value = trimToNull(value);
        if (name == null || value == null) {
            throw new IllegalArgumentException("Product attribute name and value must not be blank");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
