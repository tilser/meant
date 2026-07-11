package com.meant.api.plugin.catalog.extension.shopify;

import com.meant.api.plugin.catalog.extension.CatalogExtensionContributor;
import com.meant.api.plugin.catalog.extension.CatalogTool;
import com.meant.api.plugin.catalog.extension.shopify.dto.ShopifyCatalogExtensionArguments;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ShopifyCatalogExtensionContributor implements CatalogExtensionContributor {

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, JsonNode> contribute(CatalogTool tool, NegotiatedCapabilities activeCapabilities) {
        if (!activeCapabilities.supports(ShopifyCatalogExtensionCapability.ID)) {
            return Map.of();
        }
        ShopifyCatalogExtensionArguments arguments = new ShopifyCatalogExtensionArguments(true, true, true, true);
        return Map.of(ShopifyCatalogExtensionCapability.ID.value(), objectMapper.valueToTree(arguments));
    }
}
