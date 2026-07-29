package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Looks up the one verified Shopify Shop GID owned by a local merchant. */
public record FindMerchantShopifyIdentityQuery(@NotNull UUID merchantId) {
}
