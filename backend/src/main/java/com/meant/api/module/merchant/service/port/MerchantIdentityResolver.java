package com.meant.api.module.merchant.service.port;

import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolutionContext;
import java.util.Optional;

/** Resolves provider-backed identity evidence without exposing provider details to the merchant module. */
public interface MerchantIdentityResolver {

    Optional<MerchantIdentityResolution> resolve(MerchantIdentityResolutionContext context);
}
