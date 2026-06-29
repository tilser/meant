package com.meant.api.plugin.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NegotiatedCapabilities(
        Map<CapabilityId, String> versions
) {

    public NegotiatedCapabilities {
        versions = versions == null ? Map.of() : Map.copyOf(versions);
    }

    public static NegotiatedCapabilities none() {
        return new NegotiatedCapabilities(Map.of());
    }

    public static NegotiatedCapabilities of(Map<CapabilityId, String> versions) {
        return new NegotiatedCapabilities(versions);
    }

    public boolean supports(CapabilityId capabilityId) {
        return versions.containsKey(capabilityId);
    }

    public Optional<String> version(CapabilityId capabilityId) {
        return Optional.ofNullable(versions.get(capabilityId));
    }

    public Set<CapabilityId> ids() {
        return versions.keySet();
    }
}
