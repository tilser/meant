package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.module.merchant.service.MerchantCatalogPluginDispatchService;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantProductDetailsServiceTest {

    @Test
    void fetchesProductDetailsFromActiveMerchantWithContext() {
        UUID merchantId = UUID.randomUUID();
        FakeMerchantLookupService merchantLookupService = new FakeMerchantLookupService();
        FakeMerchantCatalogPluginDispatchService merchantCatalogPluginDispatchService =
                new FakeMerchantCatalogPluginDispatchService();
        MerchantProductDetailsService service = new MerchantProductDetailsService(
                merchantLookupService,
                merchantCatalogPluginDispatchService
        );

        ProductDetailsResult result = service.get(new GetMerchantProductDetailsQuery(
                merchantId,
                "gid://shopify/Product/1",
                "US",
                "en"
        ));

        assertThat(merchantLookupService.requestedMerchantId).isEqualTo(merchantId);
        assertThat(merchantCatalogPluginDispatchService.requestedLookupMerchant)
                .isSameAs(merchantLookupService.merchant);
        assertThat(merchantCatalogPluginDispatchService.requestedLookupProductId)
                .isEqualTo("gid://shopify/Product/1");
        assertThat(merchantCatalogPluginDispatchService.requestedGetProductMerchant)
                .isSameAs(merchantLookupService.merchant);
        assertThat(merchantCatalogPluginDispatchService.requestedGetProductId)
                .isEqualTo("gid://shopify/Product/1");
        assertThat(merchantCatalogPluginDispatchService.requestedGetProductContext.addressCountry()).isEqualTo("US");
        assertThat(merchantCatalogPluginDispatchService.requestedGetProductContext.language()).isEqualTo("en");
        assertThat(merchantCatalogPluginDispatchService.requestedGetProductContext.intent()).isEqualTo("Product detail");
        assertThat(result.product().title()).isEqualTo("Blue Shirt");
        assertThat(result.product().options()).extracting("name").containsExactly("Size");
    }

    @Test
    void forwardsVariantSelectionAndPreferencesToGetProduct() {
        UUID merchantId = UUID.randomUUID();
        FakeMerchantLookupService merchantLookupService = new FakeMerchantLookupService();
        FakeMerchantCatalogPluginDispatchService dispatchService = new FakeMerchantCatalogPluginDispatchService();
        MerchantProductDetailsService service = new MerchantProductDetailsService(
                merchantLookupService, dispatchService);

        service.get(new GetMerchantProductDetailsQuery(
                merchantId,
                "gid://shopify/Product/1",
                "US",
                "en",
                List.of(new ProductAttribute("variant-option", "Color", "Blue")),
                List.of("Prefer cotton")
        ));

        assertThat(dispatchService.requestedSelected).containsExactly(
                new ProductDetailsResponse.SelectedOption("Color", "Blue"));
        assertThat(dispatchService.requestedPreferences).containsExactly("Prefer cotton");
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
            super(null, null);
        }

        @Override
        public MerchantSemanticSearchResult activeSearchResult(UUID merchantId) {
            requestedMerchantId = merchantId;
            return merchant;
        }
    }

    private static class FakeMerchantCatalogPluginDispatchService extends MerchantCatalogPluginDispatchService {

        private MerchantSemanticSearchResult requestedLookupMerchant;
        private String requestedLookupProductId;
        private MerchantSemanticSearchResult requestedGetProductMerchant;
        private String requestedGetProductId;
        private CatalogSearchContext requestedGetProductContext;
        private List<ProductDetailsResponse.SelectedOption> requestedSelected;
        private List<String> requestedPreferences;

        FakeMerchantCatalogPluginDispatchService() {
            super(null, null);
        }

        @Override
        public CatalogLookupResult lookupCatalog(
                MerchantSemanticSearchResult merchant,
                String productId,
                CatalogSearchContext context,
                NegotiatedCapabilities activeCapabilities
        ) {
            requestedLookupMerchant = merchant;
            requestedLookupProductId = productId;
            return new CatalogLookupResult(merchant.advertisedMcpEndpoint(), productId, null, null);
        }

        @Override
        public ProductDetailsResult getProduct(
                MerchantSemanticSearchResult merchant,
                String productId,
                CatalogSearchContext context,
                NegotiatedCapabilities activeCapabilities
        ) {
            requestedGetProductMerchant = merchant;
            requestedGetProductId = productId;
            requestedGetProductContext = context;
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

        @Override
        public ProductDetailsResult getProduct(
                MerchantSemanticSearchResult merchant,
                String productId,
                List<ProductDetailsResponse.SelectedOption> selected,
                List<String> preferences,
                CatalogSearchContext context,
                NegotiatedCapabilities activeCapabilities
        ) {
            requestedSelected = selected;
            requestedPreferences = preferences;
            return getProduct(merchant, productId, context, activeCapabilities);
        }
    }
}
