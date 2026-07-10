package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import java.time.Duration;
import java.util.function.Consumer;

/** Small blocking source contract; federation owns concurrency, deadlines, cancellation, and terminal events. */
public interface CatalogDiscoverySource {

    DiscoverySourceIdentity sourceIdentity();

    Duration timeout();

    default boolean supports(CatalogDiscoveryRequest request) {
        return true;
    }

    CatalogSourceResult search(CatalogDiscoveryRequest request, Consumer<ProductCandidate> candidateConsumer);
}
