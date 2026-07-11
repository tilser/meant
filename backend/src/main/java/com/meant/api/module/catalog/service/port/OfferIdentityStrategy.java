package com.meant.api.module.catalog.service.support;

import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;

/** Provider-owned offer identity rule supplied to provider-neutral candidate mappers. */
public interface OfferIdentityStrategy {

    boolean supports(ProviderIdentity provider);

    ExternalIdentifier product(
            ProviderIdentity provider,
            ExternalIdentifier externalProductIdentity,
            ExternalIdentifier externalVariantIdentity
    );
}
