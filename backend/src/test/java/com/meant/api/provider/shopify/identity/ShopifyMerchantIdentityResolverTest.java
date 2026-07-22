package com.meant.api.provider.shopify.identity;

import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.SHOPIFY_SHOP;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.PROVIDER_ID;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.STOREFRONT_DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIntegrationProvider.SHOPIFY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolutionContext;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ShopifyMerchantIdentityResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShopifyMerchantIdentityResolver resolver = new ShopifyMerchantIdentityResolver();

    @Test
    void ignoresProfilesWithoutShopifyIdentityEvidence() throws Exception {
        UcpProfile profile = profile("""
                "com.example.wallet": [{
                  "config": {"merchant_id": "merchant-1"}
                }]
                """);

        assertThat(resolver.resolve(new MerchantIdentityResolutionContext(
                "merchant.example",
                null,
                profile
        ))).isEmpty();
    }

    @Test
    void observedShopifyMerchantWithoutProfileIdentityEvidenceFailsClosed() throws Exception {
        UcpProfile profile = profile("""
                "com.example.wallet": [{
                  "config": {"merchant_id": "merchant-1"}
                }]
                """);

        assertThatThrownBy(() -> resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.myshopify.com",
                SHOPIFY,
                "gid://shopify/Shop/17756429",
                profile
        )))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("stable shop ID");
    }

    @Test
    void observedShopifyMerchantRequiresItsProviderGid() throws Exception {
        UcpProfile profile = profile("""
                "com.google.pay": [{
                  "config": {
                    "merchant_info": {"merchant_origin": "youngla.com"},
                    "allowed_payment_methods": [{
                      "tokenization_specification": {
                        "parameters": {"gateway": "shopify", "gatewayMerchantId": "17756429"}
                      }
                    }]
                  }
                }]
                """);

        assertThatThrownBy(() -> resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.myshopify.com",
                SHOPIFY,
                null,
                profile
        )))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void resolvesCanonicalStorefrontAndVerifiedShopGid() throws Exception {
        UcpProfile profile = profile("""
                "com.google.pay": [{
                  "id": "google_pay",
                  "config": {
                    "merchant_info": {
                      "merchant_name": "YoungLA",
                      "merchant_origin": "www.youngla.com"
                    },
                    "allowed_payment_methods": [{
                      "tokenization_specification": {
                        "parameters": {
                          "gateway": "shopify",
                          "gatewayMerchantId": "17756429"
                        }
                      }
                    }]
                  }
                }],
                "dev.shopify.shop_pay": [{
                  "id": "shop_pay",
                  "config": {"shop_id": "17756429"}
                }]
                """, "https://youngla.myshopify.com/api/ucp/mcp");

        var resolution = resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.com",
                "gid://shopify/Shop/17756429",
                profile
        )).orElseThrow();

        assertThat(resolution.canonicalDomain()).isEqualTo("youngla.com");
        assertThat(resolution.merchantName()).isEqualTo("YoungLA");
        assertThat(resolution.claims()).containsExactly(
                new ResolvedMerchantIdentityClaim(SHOPIFY_SHOP, "gid://shopify/Shop/17756429", PROVIDER_ID),
                new ResolvedMerchantIdentityClaim(DOMAIN, "youngla.com", STOREFRONT_DOMAIN)
        );
    }

    @Test
    void resolvesShopIdentityFromGoogleGatewayWhenShopPayIsAbsent() throws Exception {
        UcpProfile profile = profile("""
                "com.google.pay": [{
                  "config": {
                    "merchant_info": {
                      "merchant_name": "Ridge & River",
                      "merchant_origin": "https://ridgeandriver.com/"
                    },
                    "allowed_payment_methods": [{
                      "tokenization_specification": {
                        "parameters": {
                          "gateway": "shopify",
                          "gatewayMerchantId": 58012958858
                        }
                      }
                    }]
                  }
                }]
                """);

        var resolution = resolver.resolve(new MerchantIdentityResolutionContext(
                "ridgeandriver.myshopify.com",
                null,
                profile
        )).orElseThrow();

        assertThat(resolution.canonicalDomain()).isEqualTo("ridgeandriver.com");
        assertThat(resolution.claims()).extracting(ResolvedMerchantIdentityClaim::normalizedValue)
                .containsExactly(
                        "gid://shopify/Shop/58012958858",
                        "ridgeandriver.com"
                );
    }

    @Test
    void rejectsConflictingProfileShopIds() throws Exception {
        UcpProfile profile = profile("""
                "com.google.pay": [{
                  "config": {
                    "allowed_payment_methods": [{
                      "tokenization_specification": {
                        "parameters": {
                          "gateway": "shopify",
                          "gatewayMerchantId": "17756429"
                        }
                      }
                    }]
                  }
                }],
                "dev.shopify.shop_pay": [{
                  "config": {"shop_id": "999"}
                }]
                """);

        assertThatThrownBy(() -> resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.myshopify.com",
                "gid://shopify/Shop/17756429",
                profile
        )))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("stable shop ID");
    }

    @Test
    void rejectsObservedShopGidThatDoesNotMatchTheProfile() throws Exception {
        UcpProfile profile = profile("""
                "dev.shopify.shop_pay": [{
                  "config": {"shop_id": "17756429"}
                }]
                """);

        assertThatThrownBy(() -> resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.myshopify.com",
                "gid://shopify/Shop/999",
                profile
        )))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.myshopify.com",
                "17756429",
                profile
        )))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsConflictingStorefrontOrigins() throws Exception {
        UcpProfile profile = profile("""
                "com.google.pay": [{
                  "config": {
                    "merchant_info": {"merchant_origin": "youngla.com"},
                    "allowed_payment_methods": [{
                      "tokenization_specification": {
                        "parameters": {"gateway": "shopify", "gatewayMerchantId": "17756429"}
                      }
                    }]
                  }
                }, {
                  "config": {
                    "merchant_info": {"merchant_origin": "unrelated.example"}
                  }
                }]
                """);

        assertThatThrownBy(() -> resolver.resolve(new MerchantIdentityResolutionContext(
                "youngla.myshopify.com",
                null,
                profile
        )))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("canonical storefront domain");
    }

    @Test
    void keepsVerifiedMyshopifyOriginWhenItIsTheCanonicalStorefront() throws Exception {
        UcpProfile profile = profile("""
                "com.google.pay": [{
                  "config": {
                    "merchant_info": {
                      "merchant_name": "Southeastern Homeschool Sports",
                      "merchant_origin": "55y9eh-nq.myshopify.com"
                    },
                    "allowed_payment_methods": [{
                      "tokenization_specification": {
                        "parameters": {"gateway": "shopify", "gatewayMerchantId": "75578507518"}
                      }
                    }]
                  }
                }]
                """);

        var resolution = resolver.resolve(new MerchantIdentityResolutionContext(
                "55y9eh-nq.myshopify.com",
                "gid://shopify/Shop/75578507518",
                profile
        )).orElseThrow();

        assertThat(resolution.canonicalDomain()).isEqualTo("55y9eh-nq.myshopify.com");
        assertThat(resolution.claims()).containsExactly(
                new ResolvedMerchantIdentityClaim(SHOPIFY_SHOP, "gid://shopify/Shop/75578507518", PROVIDER_ID),
                new ResolvedMerchantIdentityClaim(DOMAIN, "55y9eh-nq.myshopify.com", STOREFRONT_DOMAIN)
        );
    }

    private UcpProfile profile(String paymentHandlers) throws Exception {
        return profile(paymentHandlers, null);
    }

    private UcpProfile profile(String paymentHandlers, String mcpEndpoint) throws Exception {
        String services = mcpEndpoint == null ? "{}" : """
                {
                  "dev.ucp.shopping": [{
                    "transport": "mcp",
                    "endpoint": "%s"
                  }]
                }
                """.formatted(mcpEndpoint);
        return objectMapper.readValue("""
                {
                  "version": "2026-04-08",
                  "services": %s,
                  "payment_handlers": {
                    %s
                  }
                }
                """.formatted(services, paymentHandlers), UcpProfile.class);
    }
}
