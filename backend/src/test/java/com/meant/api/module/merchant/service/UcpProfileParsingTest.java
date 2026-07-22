package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UcpProfileParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDynamicUcpProfileMapsAndExtendsArray() throws Exception {
        UcpProfileResponse response = objectMapper.readValue("""
                {
                  "ucp": {
                    "version": "2026-01-23",
                    "supported_versions": {
                      "2026-01-23": "https://ucp.dev/2026-01-23"
                    },
                    "services": {
                      "dev.ucp.shopping": [
                        {
                          "id": "dev.ucp.shopping",
                          "version": "2026-01-23",
                          "spec": {"url": "https://ucp.dev/spec"},
                          "transport": "mcp",
                          "endpoint": "https://store.example/api/mcp",
                          "schema": "https://ucp.dev/schema"
                        },
                        {
                          "id": "dev.ucp.shopping",
                          "version": "2026-01-23",
                          "spec": {"url": "https://ucp.dev/spec"},
                          "transport": "embedded",
                          "endpoint": "https://store.example/api/embedded",
                          "schema": "https://ucp.dev/schema"
                        }
                      ]
                    },
                    "capabilities": {
                      "dev.ucp.shopping.checkout": [
                        {
                          "id": "checkout",
                          "version": "1.0.0",
                          "spec": "https://ucp.dev/checkout",
                          "schema": {"url": "https://ucp.dev/checkout-schema"},
                          "extends": ["dev.ucp.shopping.cart"],
                          "requires": {
                            "protocol": {"min": "2026-01-01", "max": "2026-12-31"},
                            "capabilities": {
                              "dev.ucp.shopping.cart": {"min": "1.0.0", "max": "2.0.0"}
                            }
                          },
                          "config": {
                            "scopes": {
                              "dev.ucp.shopping.checkout:manage": {}
                            }
                          }
                        }
                      ]
                    },
                    "payment_handlers": {
                      "com.google.pay": [
                        {
                          "id": "google-pay",
                          "version": "1.0.0",
                          "spec": "https://pay.example/spec",
                          "schema": {"url": "https://pay.example/schema"},
                          "config": {"ignored": true}
                        }
                      ]
                    }
                  }
                }
                """, UcpProfileResponse.class);

        UcpProfile profile = response.ucp();

        assertThat(profile.version()).isEqualTo("2026-01-23");
        assertThat(profile.supportedVersions()).containsEntry("2026-01-23", "https://ucp.dev/2026-01-23");
        assertThat(profile.services()).containsKey("dev.ucp.shopping");
        assertThat(profile.services().get("dev.ucp.shopping")).hasSize(2);
        assertThat(profile.services().get("dev.ucp.shopping").getFirst().transport()).isEqualTo("mcp");
        assertThat(profile.services().get("dev.ucp.shopping").getFirst().spec().url()).isEqualTo("https://ucp.dev/spec");
        assertThat(profile.services().get("dev.ucp.shopping").getFirst().schema().url()).isEqualTo("https://ucp.dev/schema");
        assertThat(profile.capabilities().get("dev.ucp.shopping.checkout").getFirst().extendsCapabilities())
                .containsExactly("dev.ucp.shopping.cart");
        assertThat(profile.capabilities().get("dev.ucp.shopping.checkout").getFirst().requires().protocol().min())
                .isEqualTo("2026-01-01");
        assertThat(profile.capabilities().get("dev.ucp.shopping.checkout").getFirst().config().has("scopes"))
                .isTrue();
        assertThat(profile.paymentHandlers().get("com.google.pay").getFirst().schema().url())
                .isEqualTo("https://pay.example/schema");
        assertThat(profile.paymentHandlers().get("com.google.pay").getFirst().config().path("ignored").asBoolean())
                .isTrue();
    }

    @Test
    void parsesExtendsStringAsSingleCapabilityName() throws Exception {
        UcpProfileResponse response = objectMapper.readValue("""
                {
                  "ucp": {
                    "version": "2026-01-23",
                    "capabilities": {
                      "dev.ucp.shopping.checkout": [
                        {
                          "version": "1.0.0",
                          "extends": "dev.ucp.shopping.cart"
                        }
                      ]
                    }
                  }
                }
                """, UcpProfileResponse.class);

        assertThat(response.ucp().capabilities().get("dev.ucp.shopping.checkout").getFirst().extendsCapabilities())
                .containsExactly("dev.ucp.shopping.cart");
    }

    @Test
    void parsesBareUcpProfileShape() throws Exception {
        UcpProfile profile = objectMapper.readValue("""
                {
                  "version": "2026-04-08",
                  "supported_versions": {
                    "2026-04-08": "https://ucp.dev/2026-04-08"
                  },
                  "services": {
                    "dev.ucp.shopping": [
                      {
                        "version": "2026-04-08",
                        "transport": "mcp",
                        "endpoint": "https://store.example/api/mcp"
                      }
                    ]
                  }
                }
                """, UcpProfile.class);

        assertThat(profile.version()).isEqualTo("2026-04-08");
        assertThat(profile.services().get("dev.ucp.shopping").getFirst().endpoint())
                .isEqualTo("https://store.example/api/mcp");
    }

    @Test
    void parsesSingleServiceObjectWithNestedRestTransport() throws Exception {
        UcpProfileResponse response = objectMapper.readValue("""
                {
                  "ucp": {
                    "version": "2026-01-11",
                    "services": {
                      "dev.ucp.shopping": {
                        "version": "2026-01-11",
                        "spec": "https://ucp.dev/specification/shopping/",
                        "rest": {
                          "schema": "https://example.com/openapi.json",
                          "endpoint": "https://api.example.com/services"
                        }
                      }
                    },
                    "capabilities": {
                      "dev.ucp.shopping.checkout": [
                        {
                          "version": "2026-01-11",
                          "spec": "https://ucp.dev/specification/shopping/checkout/",
                          "schema": "https://ucp.dev/schemas/shopping/checkout.json"
                        }
                      ]
                    }
                  }
                }
                """, UcpProfileResponse.class);

        assertThat(response.ucp().services().get("dev.ucp.shopping"))
                .singleElement()
                .satisfies(service -> {
                    assertThat(service.version()).isEqualTo("2026-01-11");
                    assertThat(service.transport()).isEqualTo("rest");
                    assertThat(service.endpoint()).isEqualTo("https://api.example.com/services");
                    assertThat(service.schema().url()).isEqualTo("https://example.com/openapi.json");
                });
    }
}
