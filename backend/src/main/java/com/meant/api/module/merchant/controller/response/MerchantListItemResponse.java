package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantListItemResult;
import java.util.UUID;

public record MerchantListItemResponse(
        UUID id,
        String domain,
        String name,
        String description,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint
) {

    public static MerchantListItemResponse from(MerchantListItemResult result) {
        return new MerchantListItemResponse(
                result.id(),
                result.domain(),
                result.name(),
                result.description(),
                result.advertisedMcpEndpoint(),
                result.profileMcpEndpoint()
        );
    }
}
