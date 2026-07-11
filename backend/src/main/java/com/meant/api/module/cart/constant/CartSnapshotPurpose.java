package com.meant.api.module.cart.constant;

/** Defines whether a persisted remote cart snapshot may invalidate an active checkout session. */
public enum CartSnapshotPurpose {
    READ_REFRESH,
    CART_MUTATION;

    public boolean invalidatesCheckout() {
        return this == CART_MUTATION;
    }
}
