package com.meant.api.module.catalog.service.dto;

public record CatalogRehydrationContext(String country, String language, String currency) {

    public CatalogRehydrationContext(String country, String language) {
        this(country, language, null);
    }
}
