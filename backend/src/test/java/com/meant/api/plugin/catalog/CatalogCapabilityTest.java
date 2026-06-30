package com.meant.api.plugin.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.getproduct.CatalogGetProductCapability;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductArguments;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductRequest;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupArguments;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupRequest;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupResponse;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchArguments;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchRequest;
import com.meant.api.plugin.catalog.shopify.ShopifyCatalogExtensionCapability;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CatalogCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchBuildsTypedArgumentsAndGatesShopifyExtension() throws Exception {
        CatalogSearchCapability capability = new CatalogSearchCapability(objectMapper);
        CatalogSearchRequest request = new CatalogSearchRequest(
                "running shoes",
                new CatalogSearchContext("US", null, null, "en", "USD", "Original request"),
                new CatalogSearchSignals("203.0.113.4", "Meant Test"),
                new CatalogSearchFilters(List.of("shoes"), new CatalogSearchPriceFilter(null, 10000L)),
                10
        );

        CatalogSearchArguments unnegotiated = capability.buildArguments(request, NegotiatedCapabilities.none());
        String unnegotiatedJson = objectMapper.writeValueAsString(unnegotiated);

        assertThat(unnegotiated.catalog().query()).isEqualTo("running shoes");
        assertThat(unnegotiated.catalog().pagination().limit()).isEqualTo(10);
        assertThat(unnegotiatedJson).contains("\"catalog\"");
        assertThat(unnegotiatedJson).doesNotContain("dev.shopify.catalog");

        CatalogSearchArguments negotiated = capability.buildArguments(request, shopifyActive());
        String negotiatedJson = objectMapper.writeValueAsString(negotiated);

        assertThat(negotiatedJson).contains("dev.shopify.catalog");
        assertThat(negotiatedJson).contains("include_product_ids");
    }

    @Test
    void searchParsesTypedResponse() {
        CatalogSearchCapability capability = new CatalogSearchCapability(objectMapper);

        CatalogSearchResponse response = capability.parseResponse(new UcpToolResponse(
                "{\"products\":[{\"id\":\"product-1\",\"title\":\"Trail Runner\"}]}",
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.products()).extracting("id").containsExactly("product-1");
    }

    @Test
    void lookupBuildsTypedArgumentsParsesResponseAndGatesShopifyExtension() throws Exception {
        CatalogLookupCapability capability = new CatalogLookupCapability(objectMapper);
        CatalogLookupRequest request = new CatalogLookupRequest(
                "gid://shopify/Product/1",
                new CatalogSearchContext("US", null, null, "en", "USD", "Product detail")
        );

        CatalogLookupArguments unnegotiated = capability.buildArguments(request, NegotiatedCapabilities.none());
        CatalogLookupArguments negotiated = capability.buildArguments(request, shopifyActive());

        assertThat(unnegotiated.catalog().ids()).containsExactly("gid://shopify/Product/1");
        assertThat(unnegotiated.catalog().context().addressCountry()).isEqualTo("US");
        assertThat(objectMapper.writeValueAsString(unnegotiated)).doesNotContain("dev.shopify.catalog");
        assertThat(objectMapper.writeValueAsString(negotiated)).contains("dev.shopify.catalog");

        CatalogLookupResponse response = capability.parseResponse(new UcpToolResponse(
                "{\"products\":[{\"id\":\"gid://shopify/Product/1\",\"title\":\"Candle\"}]}",
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.resolvedProductId("fallback")).isEqualTo("gid://shopify/Product/1");
        assertThat(response.resolvedProduct().title()).isEqualTo("Candle");
    }

    @Test
    void getProductBuildsTypedArgumentsParsesResponseAndGatesShopifyExtension() throws Exception {
        CatalogGetProductCapability capability = new CatalogGetProductCapability(objectMapper);
        CatalogGetProductRequest request = new CatalogGetProductRequest(
                "gid://shopify/Product/1",
                new CatalogSearchContext("US", null, null, "en", "USD", "Product detail")
        );

        CatalogGetProductArguments unnegotiated = capability.buildArguments(request, NegotiatedCapabilities.none());
        CatalogGetProductArguments negotiated = capability.buildArguments(request, shopifyActive());

        assertThat(unnegotiated.catalog().id()).isEqualTo("gid://shopify/Product/1");
        assertThat(unnegotiated.catalog().context().addressCountry()).isEqualTo("US");
        assertThat(objectMapper.writeValueAsString(unnegotiated)).doesNotContain("dev.shopify.catalog");
        assertThat(objectMapper.writeValueAsString(negotiated)).contains("dev.shopify.catalog");

        ProductDetailsResponse response = capability.parseResponse(new UcpToolResponse(
                "{\"product\":{\"id\":\"gid://shopify/Product/1\",\"title\":\"Candle\"}}",
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.product().productId()).isEqualTo("gid://shopify/Product/1");
        assertThat(response.product().title()).isEqualTo("Candle");
    }

    @Test
    void shopifyExtensionAdvertisesWithoutToolOwnership() {
        ShopifyCatalogExtensionCapability capability = new ShopifyCatalogExtensionCapability();

        assertThat(capability.id()).isEqualTo(ShopifyCatalogExtensionCapability.ID);
        assertThat(capability.toolNames()).isEmpty();
        assertThat(capability.advertisements()).singleElement()
                .satisfies(advertisement -> {
                    assertThat(advertisement.id()).isEqualTo(ShopifyCatalogExtensionCapability.ID);
                    assertThat(advertisement.tools()).isEmpty();
                    assertThat(advertisement.required()).isFalse();
                });
    }

    private NegotiatedCapabilities shopifyActive() {
        return NegotiatedCapabilities.of(Map.of(
                CapabilityId.of("dev.shopify.catalog"),
                "1.0.0"
        ));
    }
}
