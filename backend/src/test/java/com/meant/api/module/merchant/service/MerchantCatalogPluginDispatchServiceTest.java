package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.MerchantCatalogPluginDispatchService;
import com.meant.api.plugin.catalog.getproduct.CatalogGetProductCapability;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyCatalogExtensionCapability;
import com.meant.api.plugin.catalog.extension.CatalogExtensionRegistry;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyCatalogExtensionContributor;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantCatalogPluginDispatchServiceTest {

    @Test
    void dispatchesFullCatalogFlowThroughRegistryAndUcpTools() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCatalogPluginDispatchService service = new MerchantCatalogPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry()
        );
        MerchantSemanticSearchResult merchant = merchant();

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"search_catalog\"")))
                .andExpect(content().string(containsString("\"catalog\"")))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"products\\":[{\\"id\\":\\"gid://shopify/Product/1\\",\\"title\\":\\"Trail Runner\\"}],\\"pagination\\":{\\"has_next_page\\":true,\\"cursor\\":\\"cursor-next\\"}}"
                              }
                            ],
                            "structuredContent": {
                              "ucp": {
                                "capabilities": {
                                  "dev.shopify.catalog": "1.0.0"
                                }
                              }
                            },
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"lookup_catalog\"")))
                .andExpect(jsonPath("$.params.arguments.catalog.ids[0]").value("gid://shopify/Product/1"))
                .andExpect(jsonPath("$.params.arguments.meta['ucp-agent'].profile").exists())
                .andExpect(content().string(containsString("dev.shopify.catalog")))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 5,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"products\\":[{\\"id\\":\\"gid://shopify/Product/1\\",\\"title\\":\\"Trail Runner\\"}]}"
                              }
                            ],
                            "structuredContent": {
                              "ucp": {
                                "capabilities": {
                                  "dev.shopify.catalog": "1.0.0"
                                }
                              }
                            },
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"get_product\"")))
                .andExpect(jsonPath("$.params.arguments.catalog.id").value("gid://shopify/Product/1"))
                .andExpect(jsonPath("$.params.arguments.meta['ucp-agent'].profile").exists())
                .andExpect(content().string(not(containsString("get_product_details"))))
                .andExpect(content().string(containsString("dev.shopify.catalog")))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 6,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"product\\":{\\"id\\":\\"gid://shopify/Product/1\\",\\"title\\":\\"Trail Runner Detail\\"}}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CatalogSearchResult searchResult = service.searchCatalog(
                merchant,
                "running shoes",
                new CatalogSearchContext("US", null, null, "en", "USD", "Original request"),
                null,
                null,
                10
        );
        CatalogLookupResult lookupResult = service.lookupCatalog(
                merchant,
                searchResult.products().getFirst().id(),
                null,
                searchResult.negotiatedCapabilities()
        );
        ProductDetailsResult productResult = service.getProduct(
                merchant,
                lookupResult.productId(),
                null,
                lookupResult.negotiatedCapabilities()
        );

        assertThat(searchResult.products()).extracting("id").containsExactly("gid://shopify/Product/1");
        assertThat(searchResult.pagination()).satisfies(pagination -> {
            assertThat(pagination.hasNextPage()).isTrue();
            assertThat(pagination.cursor()).isEqualTo("cursor-next");
        });
        assertThat(searchResult.negotiatedCapabilities().supports(ShopifyCatalogExtensionCapability.ID)).isTrue();
        assertThat(lookupResult.productId()).isEqualTo("gid://shopify/Product/1");
        assertThat(productResult.product().productId()).isEqualTo("gid://shopify/Product/1");
        assertThat(productResult.product().title()).isEqualTo("Trail Runner Detail");
        server.verify();
    }

    private MerchantMcpToolClient merchantMcpToolClient(RestClient restClient) {
        return new MerchantMcpToolClient(
                restClient,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName("93.184.216.34")))
        );
    }

    private CapabilityRegistry registry() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new CapabilityRegistry(List.of(
                new CatalogSearchCapability(objectMapper, extensionRegistry()),
                new CatalogLookupCapability(objectMapper, extensionRegistry()),
                new CatalogGetProductCapability(objectMapper, extensionRegistry()),
                new ShopifyCatalogExtensionCapability()
        ));
    }

    private CatalogExtensionRegistry extensionRegistry() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new CatalogExtensionRegistry(List.of(new ShopifyCatalogExtensionContributor(objectMapper)));
    }

    private MerchantSemanticSearchResult merchant() {
        return new MerchantSemanticSearchResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/api/mcp",
                null,
                "Merchant",
                0.9d,
                0.8d,
                1
        );
    }
}
