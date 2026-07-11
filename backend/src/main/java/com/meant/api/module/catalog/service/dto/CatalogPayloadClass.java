package com.meant.api.module.catalog.service.dto;

/** Legally and operationally distinct classes of provider-supplied catalog data. */
public enum CatalogPayloadClass {
    SEARCH_FACTS,
    SEARCH_MEDIA,
    IDENTIFIERS_PROVENANCE,
    SAVED_INTERACTION,
    TRANSACTION_SNAPSHOT
}
