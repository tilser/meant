package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;

public record AgentProductReadSelection(
        UserProductDetailResult detail,
        Offer offer,
        MerchantIntegrationResult merchantIntegration
) {
}
