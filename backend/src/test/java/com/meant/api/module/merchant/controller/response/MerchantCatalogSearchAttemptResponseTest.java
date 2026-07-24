package com.meant.api.module.merchant.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantCatalogSearchAttemptResponseTest {

    @Test
    void sanitizesTransportCoordinatesInBuyerVisibleSearchErrors() {
        MerchantCatalogSearchAttemptResult result = new MerchantCatalogSearchAttemptResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                1,
                "https://seller.myshopify.com/api/ucp/mcp",
                0,
                "Search failed at seller.myshopify.com via /api/mcp"
        );

        MerchantCatalogSearchAttemptResponse response =
                MerchantCatalogSearchAttemptResponse.from(result);

        assertThat(response.error())
                .isEqualTo("Search failed at merchant.example via merchant.example")
                .doesNotContain("myshopify.com", "/api/mcp");
    }
}
