package com.meant.api.plugin.transport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentProfile(
        @JsonProperty("profile_url") URI profileUrl,
        @JsonProperty("protocol_version") String protocolVersion,
        @JsonProperty("supported_versions") Map<String, String> supportedVersions,
        @JsonProperty("signing_key_id") String signingKeyId,
        List<CapabilityAdvertisement> capabilities
) {

    public AgentProfile {
        Objects.requireNonNull(profileUrl, "profileUrl must not be null");
        protocolVersion = requireText(protocolVersion, "protocolVersion");
        supportedVersions = immutableLinkedMap(supportedVersions);
        signingKeyId = requireText(signingKeyId, "signingKeyId");
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public static AgentProfile from(AgentIdentity identity, List<CapabilityAdvertisement> capabilities) {
        return new AgentProfile(
                identity.profileUrl(),
                identity.protocolVersion(),
                Map.of(identity.protocolVersion(), protocolSpecUrl(identity.protocolVersion())),
                identity.signingKeyId(),
                capabilities
        );
    }

    private static String protocolSpecUrl(String protocolVersion) {
        return "https://ucp.dev/" + protocolVersion;
    }

    private static Map<String, String> immutableLinkedMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
