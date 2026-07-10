package com.meant.api.plugin.catalog.common.support;

import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;

/** Provider-owned offer identity rule supplied to provider-neutral candidate mappers. */
public interface OfferIdentityStrategy {

    boolean supports(ProviderIdentity provider);

    ExternalIdentifier product(
            ProviderIdentity provider,
            ExternalIdentifier externalProductIdentity,
            ExternalIdentifier externalVariantIdentity
    );
}
