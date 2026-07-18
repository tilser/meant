package com.meant.api.module.catalog.service.port;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import java.util.Optional;

/** Provider-owned policy for resolving a canonical product to a trusted similarity item reference. */
public interface CatalogSimilarityReferenceResolver {

    Optional<CatalogSimilarityReference> resolve(CanonicalProduct product);
}
