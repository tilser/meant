package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.port.CatalogPurchaseReferencePolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Applies every provider-owned purchase-reference constraint that supports the reference source. */
@Service
@RequiredArgsConstructor
public class CatalogPurchaseReferencePolicyResolver {
    private final List<CatalogPurchaseReferencePolicy> policies;

    public boolean allows(CatalogProductReference reference) {
        return reference != null && policies.stream()
                .filter(policy -> policy.supports(reference.discoverySource()))
                .allMatch(policy -> policy.allows(reference));
    }
}
