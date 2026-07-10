package com.meant.api.plugin.catalog.common.dto;

import java.util.UUID;

/** Optional resolved Meant routing link used after discovery for merchant-owned execution. */
public record LocalMerchantRouting(UUID merchantIntegrationId) {

    public LocalMerchantRouting {
        if (merchantIntegrationId == null) {
            throw new IllegalArgumentException("Local merchant routing needs a MerchantIntegration id");
        }
    }
}
