package com.meant.api.module.catalog.service.port;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
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
