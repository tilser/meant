package com.meant.api.module.merchant.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantSemanticSearchResponseTest {

    @Test
    void exposesOnlyTheVerifiedOriginAcrossMerchantSearchText() {
        MerchantSemanticSearchResponse response = MerchantSemanticSearchResponse.from(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "nycfactory.com",
                        "seller.myshopify.com",
                        "https://seller.myshopify.com/api/ucp/mcp",
                        "https://profile.transport.example/.well-known/ucp",
                        "Search seller.myshopify.com via profile.transport.example",
                        0.9,
                        0.8,
                        1
                )
        );

        assertThat(response.domain()).isEqualTo("nycfactory.com");
        assertThat(response.name()).isEqualTo("nycfactory.com");
        assertThat(response.retrievalContent())
                .isEqualTo("Search nycfactory.com via nycfactory.com");
        assertThat(response.toString())
                .doesNotContain("seller.myshopify.com", "profile.transport.example");
    }
}
