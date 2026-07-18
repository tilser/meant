package com.meant.api.plugin.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.extension.CatalogExtensionContributor;
import com.meant.api.plugin.catalog.extension.CatalogExtensionRegistry;
import com.meant.api.plugin.catalog.extension.CatalogTool;
import com.meant.api.plugin.catalog.getproduct.CatalogGetProductCapability;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductArguments;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductFilters;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductRequest;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupArguments;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupRequest;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupResponse;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchArguments;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchRequest;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyCatalogExtensionCapability;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyCatalogExtensionContributor;
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
        CatalogSearchCapability capability = new CatalogSearchCapability(objectMapper, extensionRegistry());
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
        CatalogSearchCapability capability = new CatalogSearchCapability(objectMapper, extensionRegistry());

        CatalogSearchResponse response = capability.parseResponse(new UcpToolResponse(
                "{\"products\":[{\"id\":\"product-1\",\"title\":\"Trail Runner\"}]}",
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.products()).extracting("id").containsExactly("product-1");
    }

    @Test
    void searchParsesLegacyCatalogShapesIntoConcreteTypesAndRoundTripsCanonicalJson() throws Exception {
        CatalogSearchCapability capability = new CatalogSearchCapability(objectMapper, extensionRegistry());

        CatalogSearchResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "products": [
                            {
                              "id": "product-1",
                              "title": "Trail Runner",
                              "list_price": {"amount": "149.95", "currency_code": "USD"},
                              "aggregate_rating": {
                                "rating_value": "4.8",
                                "scale_max": 5,
                                "rating_count": "214"
                              },
                              "review_count": {"count": "214"},
                              "skus": {"sku": "RUN-BLK-10"},
                              "certifications": "B Corp",
                              "materials": [{"name": "Recycled polyester"}],
                              "collections": ["Trail"],
                              "metadata": {"fit": "true to size"},
                              "metafields": {"custom": {"terrain": "trail"}},
                              "tech_specs": {"value": "8 mm drop"}
                            }
                          ]
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        CatalogSearchResponse.Product product = response.products().getFirst();
        assertThat(product.listPrice().amount()).isEqualTo(14995L);
        assertThat(product.listPrice().currency()).isEqualTo("USD");
        assertThat(product.rating().value()).isEqualTo(4.8d);
        assertThat(product.rating().scaleMax()).isEqualTo(5.0d);
        assertThat(product.rating().count()).isEqualTo(214);
        assertThat(product.reviewCount()).isEqualTo(214);
        assertThat(product.skus()).containsExactly("RUN-BLK-10");
        assertThat(product.certifications()).containsExactly("B Corp");
        assertThat(product.materials()).containsExactly("Recycled polyester");
        assertThat(product.collections()).containsExactly("Trail");
        assertThat(product.techSpecs()).containsExactly("8 mm drop");
        assertThat(product.metadata().get("fit").asString()).isEqualTo("true to size");
        assertThat(product.metafields().get("custom").get("terrain").asString()).isEqualTo("trail");

        CatalogSearchResponse roundTripped = objectMapper.readValue(
                objectMapper.writeValueAsString(response), CatalogSearchResponse.class);
        CatalogSearchResponse.Product canonical = roundTripped.products().getFirst();
        assertThat(canonical.listPrice().amount()).isEqualTo(14995L);
        assertThat(canonical.rating()).isEqualTo(product.rating());
        assertThat(canonical.reviewCount()).isEqualTo(214);
        assertThat(canonical.metadata()).isEqualTo(product.metadata());
    }

    @Test
    void lookupBuildsTypedArgumentsParsesResponseAndGatesShopifyExtension() throws Exception {
        CatalogLookupCapability capability = new CatalogLookupCapability(objectMapper, extensionRegistry());
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
    void lookupParsesShopifyCatalogProductObjects() {
        CatalogLookupCapability capability = new CatalogLookupCapability(objectMapper, extensionRegistry());

        CatalogLookupResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "products": [
                            {
                              "id": "gid://shopify/Product/1",
                              "title": "Runner",
                              "description": {
                                "html": "Weather-ready wool runner."
                              },
                              "media": [
                                {
                                  "type": "image",
                                  "url": "https://example.test/runner.jpg",
                                  "alt_text": "Runner profile"
                                }
                              ],
                              "price_range": {
                                "min": {
                                  "amount": 6500,
                                  "currency": "USD"
                                },
                                "max": {
                                  "amount": 13000,
                                  "currency": "USD"
                                }
                              },
                              "options": [
                                {
                                  "name": "Size",
                                  "values": [
                                    {
                                      "label": "8"
                                    },
                                    {
                                      "label": "9"
                                    }
                                  ]
                                }
                              ],
                              "variants": [
                                {
                                  "id": "gid://shopify/ProductVariant/1",
                                  "title": "8",
                                  "price": {
                                    "amount": 6500,
                                    "currency": "USD"
                                  },
                                  "availability": {
                                    "available": true
                                  },
                                  "options": [
                                    {
                                      "name": "Size",
                                      "label": "8"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        ProductDetailsResponse.Product product = response.resolvedProduct();
        assertThat(product.productId()).isEqualTo("gid://shopify/Product/1");
        assertThat(product.description()).isEqualTo("Weather-ready wool runner.");
        assertThat(product.imageUrl()).isEqualTo("https://example.test/runner.jpg");
        assertThat(product.media()).singleElement()
                .satisfies(media -> {
                    assertThat(media.type()).isEqualTo("image");
                    assertThat(media.url()).isEqualTo("https://example.test/runner.jpg");
                    assertThat(media.altText()).isEqualTo("Runner profile");
                });
        assertThat(product.priceRange().min()).isEqualTo("65.00");
        assertThat(product.priceRange().max()).isEqualTo("130.00");
        assertThat(product.priceRange().currency()).isEqualTo("USD");
        assertThat(product.options().getFirst().values()).containsExactly("8", "9");
        assertThat(product.selectedOrFirstAvailableVariant().variantId()).isEqualTo("gid://shopify/ProductVariant/1");
        assertThat(product.selectedOrFirstAvailableVariant().price()).isEqualTo("65.00");
        assertThat(product.selectedOrFirstAvailableVariant().currency()).isEqualTo("USD");
        assertThat(product.selectedOrFirstAvailableVariant().available()).isTrue();
        assertThat(product.selectedOrFirstAvailableVariant().selectedOptions().getFirst().value()).isEqualTo("8");
    }

    @Test
    void getProductBuildsTypedArgumentsParsesResponseAndGatesShopifyExtension() throws Exception {
        CatalogGetProductCapability capability = new CatalogGetProductCapability(objectMapper, extensionRegistry());
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
    void getProductBuildsSelectionHintsAndRequestsUnavailableVariants() throws Exception {
        CatalogGetProductCapability capability = new CatalogGetProductCapability(objectMapper, extensionRegistry());
        CatalogGetProductRequest request = new CatalogGetProductRequest(
                "gid://shopify/Product/1",
                List.of(new ProductDetailsResponse.SelectedOption("Color", "Blue")),
                List.of("Prefer cotton"),
                new CatalogSearchContext("US", null, null, "en", "USD", "Product detail"),
                new CatalogGetProductFilters(false)
        );

        CatalogGetProductArguments arguments = capability.buildArguments(request, NegotiatedCapabilities.none());

        assertThat(arguments.catalog().selected()).containsExactly(
                new ProductDetailsResponse.SelectedOption("Color", "Blue"));
        assertThat(arguments.catalog().preferences()).containsExactly("Prefer cotton");
        assertThat(arguments.catalog().filters().available()).isFalse();
        assertThat(objectMapper.writeValueAsString(arguments)).contains("\"available\":false");
    }

    @Test
    void getProductParsesShopifyCatalogProductObject() {
        CatalogGetProductCapability capability = new CatalogGetProductCapability(objectMapper, extensionRegistry());

        ProductDetailsResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "product": {
                            "id": "gid://shopify/Product/1",
                            "title": "Runner",
                            "description": {
                              "html": "Weather-ready wool runner."
                            },
                            "price_range": {
                              "min": {
                                "amount": 6500,
                                "currency": "USD"
                              },
                              "max": {
                                "amount": 6500,
                                "currency": "USD"
                              }
                            },
                            "total_variants": 12,
                            "options": [
                              {
                                "name": "Size",
                                "values": [
                                  {
                                    "label": "5",
                                    "available": false,
                                    "exists": true
                                  }
                                ]
                              }
                            ],
                            "selected": [
                              {
                                "name": "Size",
                                "label": "5"
                              }
                            ],
                            "variants": [
                              {
                                "id": "gid://shopify/ProductVariant/1",
                                "sku": "A10990W050",
                                "title": "5",
                                "price": {
                                  "amount": 6500,
                                  "currency": "USD"
                                },
                                "availability": {
                                  "available": true
                                }
                              }
                            ]
                          }
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        ProductDetailsResponse.Product product = response.product();
        assertThat(product.productId()).isEqualTo("gid://shopify/Product/1");
        assertThat(product.description()).isEqualTo("Weather-ready wool runner.");
        assertThat(product.priceRange().min()).isEqualTo("65.00");
        assertThat(product.totalVariants()).isEqualTo(12);
        assertThat(product.options().getFirst().valueDetails()).singleElement().satisfies(value -> {
            assertThat(value.value()).isEqualTo("5");
            assertThat(value.available()).isFalse();
            assertThat(value.exists()).isTrue();
        });
        assertThat(product.selected()).containsExactly(new ProductDetailsResponse.SelectedOption("Size", "5"));
        assertThat(product.selectedOrFirstAvailableVariant().variantId()).isEqualTo("gid://shopify/ProductVariant/1");
        assertThat(product.selectedOrFirstAvailableVariant().sku()).isEqualTo("A10990W050");
    }

    @Test
    void getProductParsesGroupedMajorUnitMoneyStrings() {
        CatalogGetProductCapability capability = new CatalogGetProductCapability(objectMapper, extensionRegistry());

        ProductDetailsResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "product": {
                            "id": "gid://shopify/Product/1",
                            "title": "Runner",
                            "price_range": {
                              "min": {
                                "amount": "1,234",
                                "currency": "USD"
                              },
                              "max": {
                                "amount": "1,234",
                                "currency": "USD"
                              }
                            },
                            "variants": [
                              {
                                "id": "gid://shopify/ProductVariant/1",
                                "title": "Default",
                                "price": {
                                  "amount": "1,234",
                                  "currency": "USD"
                                }
                              }
                            ]
                          }
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        ProductDetailsResponse.Product product = response.product();
        assertThat(product.priceRange().min()).isEqualTo("1234.00");
        assertThat(product.priceRange().max()).isEqualTo("1234.00");
        assertThat(product.selectedOrFirstAvailableVariant().price()).isEqualTo("1234.00");
    }

    @Test
    void getProductParsesWholeNumberFloatingMoneyAsMinorUnits() {
        CatalogGetProductCapability capability = new CatalogGetProductCapability(objectMapper, extensionRegistry());

        ProductDetailsResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "product": {
                            "id": "gid://shopify/Product/1",
                            "title": "Runner",
                            "price_range": {
                              "min": {
                                "amount": 6500.0,
                                "currency": "USD"
                              },
                              "max": {
                                "amount": 6500.0,
                                "currency": "USD"
                              }
                            },
                            "variants": [
                              {
                                "id": "gid://shopify/ProductVariant/1",
                                "title": "Default",
                                "price": {
                                  "amount": 6500.0,
                                  "currency": "USD"
                                }
                              }
                            ]
                          }
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        ProductDetailsResponse.Product product = response.product();
        assertThat(product.priceRange().min()).isEqualTo("65.00");
        assertThat(product.priceRange().max()).isEqualTo("65.00");
        assertThat(product.selectedOrFirstAvailableVariant().price()).isEqualTo("65.00");
    }

    @Test
    void productDetailsParsesTypedRatingSellingPlansAndMetadata() throws Exception {
        ProductDetailsResponse response = objectMapper.readValue(
                """
                        {
                          "product": {
                            "id": "product-1",
                            "title": "Coffee subscription",
                            "list_price": {"amount": 2499, "currency": "USD"},
                            "rating": {"value": 4.7, "scale_max": 5, "count": 93},
                            "review_count": "93",
                            "selling_plan_groups": [
                              {
                                "id": "subscriptions",
                                "name": "Subscribe and save",
                                "app_name": "Subscriptions",
                                "options": [{"name": "Delivery", "values": ["Monthly"]}],
                                "selling_plans": [
                                  {
                                    "id": "monthly",
                                    "name": "Monthly",
                                    "description": "Delivered monthly",
                                    "options": [{"name": "Delivery", "value": "Monthly"}]
                                  }
                                ]
                              }
                            ],
                            "skus": "COFFEE-MONTHLY",
                            "materials": [{"value": "Arabica"}],
                            "metadata": {"roast": "medium"},
                            "metafields": {"custom": {"origin": "Colombia"}},
                            "tech_specs": ["1 kg"]
                          }
                        }
                        """,
                ProductDetailsResponse.class
        );

        ProductDetailsResponse.Product product = response.product();
        assertThat(product.listPrice()).isEqualTo(new ProductDetailsResponse.Money(2499L, "USD"));
        assertThat(product.rating().value()).isEqualTo(4.7d);
        assertThat(product.rating().count()).isEqualTo(93);
        assertThat(product.reviewCount()).isEqualTo(93);
        assertThat(product.skus()).containsExactly("COFFEE-MONTHLY");
        assertThat(product.materials()).containsExactly("Arabica");
        assertThat(product.metadata().get("roast").asString()).isEqualTo("medium");
        assertThat(product.metafields().get("custom").get("origin").asString()).isEqualTo("Colombia");
        assertThat(product.sellingPlanGroups()).singleElement().satisfies(group -> {
            assertThat(group.id()).isEqualTo("subscriptions");
            assertThat(group.appName()).isEqualTo("Subscriptions");
            assertThat(group.options().getFirst().values()).containsExactly("Monthly");
            assertThat(group.sellingPlans().getFirst().options().getFirst().value()).isEqualTo("Monthly");
        });
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

    @Test
    void registrySerializesMultipleExtensionsDeterministically() throws Exception {
        CatalogExtensionContributor etsyContributor = new CatalogExtensionContributor() {
            @Override
            public Map<String, tools.jackson.databind.JsonNode> contribute(
                    CatalogTool tool,
                    NegotiatedCapabilities activeCapabilities
            ) {
                if (!activeCapabilities.supports(CapabilityId.of("dev.etsy.catalog"))) {
                    return Map.of();
                }
                return Map.of("dev.etsy.catalog", objectMapper.valueToTree(Map.of("include_market", true)));
            }

            @Override
            public int order() {
                return -1;
            }
        };
        CatalogExtensionRegistry registry = new CatalogExtensionRegistry(List.of(
                new ShopifyCatalogExtensionContributor(objectMapper),
                etsyContributor
        ));

        String serialized = objectMapper.writeValueAsString(registry.extensions(
                CatalogTool.SEARCH,
                NegotiatedCapabilities.of(Map.of(
                        CapabilityId.of("dev.shopify.catalog"), "1.0.0",
                        CapabilityId.of("dev.etsy.catalog"), "1.0.0"
                ))
        ));

        assertThat(serialized).isEqualTo(
                "{\"dev.etsy.catalog\":{\"include_market\":true},"
                        + "\"dev.shopify.catalog\":{\"include_product_ids\":true,"
                        + "\"include_variant_ids\":true,\"include_selling_plans\":true,"
                        + "\"include_metafields\":true}}"
        );
    }

    private CatalogExtensionRegistry extensionRegistry() {
        return new CatalogExtensionRegistry(List.of(new ShopifyCatalogExtensionContributor(objectMapper)));
    }

    private NegotiatedCapabilities shopifyActive() {
        return NegotiatedCapabilities.of(Map.of(
                CapabilityId.of("dev.shopify.catalog"),
                "1.0.0"
        ));
    }
}
