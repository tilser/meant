package com.meant.api.plugin.catalog.common.dto;

/** Variant and configuration facts that can veto an otherwise plausible cross-source match. */
public enum ProductIdentityContradictionKind {
    SIZE,
    COLOR,
    BUNDLE,
    PACK_QUANTITY,
    GENERATION,
    MODEL,
    SELLING_PLAN
}
