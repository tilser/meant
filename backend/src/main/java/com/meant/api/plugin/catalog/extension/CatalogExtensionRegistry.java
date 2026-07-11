package com.meant.api.plugin.catalog.extension;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class CatalogExtensionRegistry {

    private final List<CatalogExtensionContributor> contributors;

    public CatalogExtensionRegistry(List<CatalogExtensionContributor> contributors) {
        List<CatalogExtensionContributor> ordered = new ArrayList<>(contributors);
        ordered.sort(Comparator.comparingInt(CatalogExtensionContributor::order)
                .thenComparing(contributor -> contributor.getClass().getName()));
        this.contributors = List.copyOf(ordered);
    }

    public Map<String, JsonNode> extensions(CatalogTool tool, NegotiatedCapabilities activeCapabilities) {
        Map<String, JsonNode> extensions = new LinkedHashMap<>();
        NegotiatedCapabilities negotiated = activeCapabilities == null
                ? NegotiatedCapabilities.none()
                : activeCapabilities;
        for (CatalogExtensionContributor contributor : contributors) {
            Map<String, JsonNode> contribution = contributor.contribute(tool, negotiated);
            if (contribution == null || contribution.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : new TreeMap<>(contribution).entrySet()) {
                if (extensions.containsKey(entry.getKey())) {
                    throw new IllegalStateException("Duplicate catalog extension contribution: " + entry.getKey());
                }
                extensions.put(entry.getKey(), entry.getValue());
            }
        }
        return extensions.isEmpty()
                ? null
                : Collections.unmodifiableMap(extensions);
    }
}
