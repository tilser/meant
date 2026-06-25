package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantProductDetailsServiceTest {

    @Test
    void fetchesProductDetailsFromActiveMerchantWithContext() {
        UUID merchantId = UUID.randomUUID();
        FakeMerchantLookupService merchantLookupService = new FakeMerchantLookupService();
        FakeMerchantProductDetailsClient merchantProductDetailsClient = new FakeMerchantProductDetailsClient();
        MerchantProductDetailsService service = new MerchantProductDetailsService(
                merchantLookupService,
                merchantProductDetailsClient
        );

        ProductDetailsResult result = service.get(new GetMerchantProductDetailsQuery(
                merchantId,
                "gid://shopify/Product/1",
                "US",
                "en"
        ));

        assertThat(merchantLookupService.requestedMerchantId).isEqualTo(merchantId);
        assertThat(merchantProductDetailsClient.requestedMerchant).isSameAs(merchantLookupService.merchant);
        assertThat(merchantProductDetailsClient.requestedProductId).isEqualTo("gid://shopify/Product/1");
        assertThat(merchantProductDetailsClient.requestedContext.addressCountry()).isEqualTo("US");
        assertThat(merchantProductDetailsClient.requestedContext.language()).isEqualTo("en");
        assertThat(merchantProductDetailsClient.requestedContext.intent()).isEqualTo("Product detail");
        assertThat(result.product().title()).isEqualTo("Blue Shirt");
        assertThat(result.product().options()).extracting("name").containsExactly("Size");
    }

    private static class FakeMerchantLookupService extends MerchantLookupService {

        private final MerchantSemanticSearchResult merchant = new MerchantSemanticSearchResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/api/mcp",
                null,
                "Merchant",
                1.0d,
                1.0d,
                1
        );
        private UUID requestedMerchantId;

        FakeMerchantLookupService() {
            super(null);
        }

        @Override
        public MerchantSemanticSearchResult activeSearchResult(UUID merchantId) {
            requestedMerchantId = merchantId;
            return merchant;
        }
    }

    private static class FakeMerchantProductDetailsClient extends MerchantProductDetailsClient {

        private MerchantSemanticSearchResult requestedMerchant;
        private String requestedProductId;
        private CatalogSearchContext requestedContext;

        FakeMerchantProductDetailsClient() {
            super(null, null);
        }

        @Override
        public ProductDetailsResult getProductDetails(
                MerchantSemanticSearchResult merchant,
                String productId,
                CatalogSearchContext context
        ) {
            requestedMerchant = merchant;
            requestedProductId = productId;
            requestedContext = context;
            return new ProductDetailsResult(
                    merchant.advertisedMcpEndpoint(),
                    "{}",
                    new ProductDetailsResponse.Product(
                            productId,
                            "Blue Shirt",
                            "Soft cotton shirt",
                            "https://merchant.example/products/blue-shirt",
                            "https://merchant.example/products/blue-shirt.jpg",
                            List.of(new ProductDetailsResponse.Image(
                                    "https://merchant.example/products/blue-shirt.jpg",
                                    "Blue shirt"
                            )),
                            List.of(new ProductDetailsResponse.Option("Size", List.of("S", "M", "L"))),
                            3,
                            new ProductDetailsResponse.PriceRange("85.00", "85.00", "USD"),
                            false,
                            List.of(),
                            new ProductDetailsResponse.SelectedVariant(
                                    "gid://shopify/ProductVariant/1",
                                    "M",
                                    "85.00",
                                    "USD",
                                    "https://merchant.example/products/blue-shirt.jpg",
                                    "Blue shirt",
                                    true,
                                    List.of(new ProductDetailsResponse.SelectedOption("Size", "M"))
                            )
                    )
            );
        }
    }
}
