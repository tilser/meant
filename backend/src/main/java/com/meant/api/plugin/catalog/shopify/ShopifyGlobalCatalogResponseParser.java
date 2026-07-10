package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogSourceOperation;
import com.meant.api.plugin.catalog.common.exception.ShopifyGlobalCatalogContractException;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.CapabilityVersion;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Product;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Variant;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ShopifyGlobalCatalogResponseParser {

    private final ObjectMapper objectMapper;
    private final ShopifyGlobalCatalogProperties properties;

    public ShopifyGlobalCatalogResponseParser(ObjectMapper objectMapper, ShopifyGlobalCatalogProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ParsedResponse parse(
            UcpToolResponse response,
            CapabilityId requiredCapability,
            CatalogSourceOperation operation
    ) {
        ShopifyGlobalCatalogResponse payload = payload(response);
        validateEnvelope(payload, requiredCapability);
        if (operation == CatalogSourceOperation.GET_PRODUCT && payload.product() == null) {
            throw new ShopifyGlobalCatalogContractException(
                    "Shopify Global Catalog get_product response was missing its required product"
            );
        }
        validateProducts(payload.resolvedProducts());
        return new ParsedResponse(payload, negotiatedCapabilities(payload));
    }

    public ParsedResponse parse(UcpToolResponse response, CapabilityId requiredCapability) {
        return parse(response, requiredCapability, null);
    }

    private ShopifyGlobalCatalogResponse payload(UcpToolResponse response) {
        if (response == null) {
            throw new ShopifyGlobalCatalogContractException("Shopify Global Catalog response was empty");
        }
        try {
            if (response.structuredContent() != null) {
                return objectMapper.convertValue(response.structuredContent(), ShopifyGlobalCatalogResponse.class);
            }
            if (response.textContent() != null && !response.textContent().isBlank()) {
                return objectMapper.readValue(response.textContent(), ShopifyGlobalCatalogResponse.class);
            }
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new ShopifyGlobalCatalogContractException(
                    "Shopify Global Catalog response did not match the typed contract",
                    exception
            );
        }
        throw new ShopifyGlobalCatalogContractException("Shopify Global Catalog response had no structured payload");
    }

    private void validateEnvelope(ShopifyGlobalCatalogResponse response, CapabilityId requiredCapability) {
        if (response == null || response.ucp() == null) {
            throw new ShopifyGlobalCatalogContractException("Shopify Global Catalog response was missing UCP metadata");
        }
        if (!properties.protocolVersion().equals(response.ucp().version())) {
            throw new ShopifyGlobalCatalogContractException("Shopify Global Catalog returned an unexpected UCP version");
        }
        requireCapability(response, requiredCapability.value(), properties.protocolVersion());
        requireCapability(response, properties.extensionId(), properties.extensionVersion());
    }

    private void requireCapability(ShopifyGlobalCatalogResponse response, String capability, String expectedVersion) {
        List<CapabilityVersion> versions = response.ucp().capabilities() == null
                ? null
                : response.ucp().capabilities().get(capability);
        boolean supported = versions != null && versions.stream()
                .anyMatch(version -> version != null && expectedVersion.equals(version.version()));
        if (!supported) {
            throw new ShopifyGlobalCatalogContractException(
                    "Shopify Global Catalog response was missing required negotiated capability " + capability
            );
        }
    }

    private void validateProducts(List<Product> products) {
        for (Product product : products) {
            if (product == null || blank(product.id()) || blank(product.title())) {
                throw new ShopifyGlobalCatalogContractException("Shopify catalog product identity or title was missing");
            }
            if (product.variants() == null || product.variants().isEmpty()) {
                throw new ShopifyGlobalCatalogContractException("Shopify catalog product had no seller variants");
            }
            for (Variant variant : product.variants()) {
                if (variant == null
                        || blank(variant.id())
                        || variant.seller() == null
                        || blank(variant.seller().id())
                        || blank(variant.seller().name())) {
                    throw new ShopifyGlobalCatalogContractException(
                            "Shopify catalog variant identity or external seller identity/name was missing"
                    );
                }
                if (variant.price() == null || variant.price().amount() == null || blank(variant.price().currency())) {
                    throw new ShopifyGlobalCatalogContractException("Shopify catalog variant price was missing");
                }
                if (variant.price().amount() < 0) {
                    throw new ShopifyGlobalCatalogContractException("Shopify catalog variant price was negative");
                }
                if (variant.listPrice() != null
                        && variant.listPrice().amount() != null
                        && variant.listPrice().amount() < 0) {
                    throw new ShopifyGlobalCatalogContractException(
                            "Shopify catalog variant list price was negative"
                    );
                }
            }
        }
    }

    private NegotiatedCapabilities negotiatedCapabilities(ShopifyGlobalCatalogResponse response) {
        Map<CapabilityId, String> capabilities = new LinkedHashMap<>();
        if (response.ucp().capabilities() != null) {
            response.ucp().capabilities().forEach((id, versions) -> {
                String version = versions == null
                        ? ""
                        : versions.stream()
                                .filter(value -> value != null && value.version() != null)
                                .map(CapabilityVersion::version)
                                .findFirst()
                                .orElse("");
                capabilities.put(CapabilityId.of(id), version);
            });
        }
        return NegotiatedCapabilities.of(capabilities);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ParsedResponse(
            ShopifyGlobalCatalogResponse payload,
            NegotiatedCapabilities negotiatedCapabilities
    ) {
    }
}
