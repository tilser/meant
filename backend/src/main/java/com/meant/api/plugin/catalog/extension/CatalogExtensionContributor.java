package com.meant.api.plugin.catalog.extension;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public interface CatalogExtensionContributor {

    Map<String, JsonNode> contribute(CatalogTool tool, NegotiatedCapabilities activeCapabilities);

    default int order() {
        return 0;
    }
}
