package com.meant.api.module.merchant.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantListItemResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantListItemResponseTest {

    @Test
    void usesOfficialDomainForTechnicalMerchantNamesAndDescriptions() {
        MerchantListItemResult result = new MerchantListItemResult(
                UUID.randomUUID(),
                "merchant.example",
                "seller.myshopify.com",
                "Profile https://transport.example/.well-known/ucp",
                "https://seller.myshopify.com/api/ucp/mcp",
                "https://transport.example/.well-known/ucp",
                true
        );

        MerchantListItemResponse response = MerchantListItemResponse.from(result);

        assertThat(response.domain()).isEqualTo("merchant.example");
        assertThat(response.name()).isEqualTo("merchant.example");
        assertThat(response.description()).isEqualTo("Profile merchant.example");
    }

    @Test
    void preservesAProviderVerifiedMyshopifyStorefrontDomain() {
        MerchantListItemResponse response = MerchantListItemResponse.from(
                new MerchantListItemResult(
                        UUID.randomUUID(),
                        "official-store.myshopify.com",
                        "Official Store",
                        "Storefront",
                        "https://transport.example/api/ucp/mcp",
                        "https://transport.example/.well-known/ucp",
                        true
                )
        );

        assertThat(response.domain()).isEqualTo("official-store.myshopify.com");
    }
}
