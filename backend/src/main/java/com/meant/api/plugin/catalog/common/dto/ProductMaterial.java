package com.meant.api.plugin.catalog.common.dto;

public record ProductMaterial(String name, Integer percentageBasisPoints) {

    public ProductMaterial {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product material name must not be blank");
        }
        name = name.trim();
        if (percentageBasisPoints != null && (percentageBasisPoints < 0 || percentageBasisPoints > 10_000)) {
            throw new IllegalArgumentException("Material percentage must be between 0 and 10000 basis points");
        }
    }
}
