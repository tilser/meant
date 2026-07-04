package com.meant.api.plugin.transport.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.signing.PublicSigningKey;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentProfile(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("profile_url")
        URI profileUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("protocol_version")
        String protocolVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("supported_versions")
        Map<String, String> supportedVersions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("signing_key_id")
        String signingKeyId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("signing_keys")
        List<PublicSigningKey> signingKeys,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<CapabilityAdvertisement> capabilities
) {

    public AgentProfile {
        Objects.requireNonNull(profileUrl, "profileUrl must not be null");
        protocolVersion = requireText(protocolVersion, "protocolVersion");
        supportedVersions = immutableLinkedMap(supportedVersions);
        signingKeyId = requireText(signingKeyId, "signingKeyId");
        signingKeys = signingKeys == null ? List.of() : List.copyOf(signingKeys);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public static AgentProfile from(AgentIdentity identity, List<CapabilityAdvertisement> capabilities) {
        return from(identity, capabilities, List.of());
    }

    public static AgentProfile from(
            AgentIdentity identity,
            List<CapabilityAdvertisement> capabilities,
            List<PublicSigningKey> signingKeys
    ) {
        return new AgentProfile(
                identity.profileUrl(),
                identity.protocolVersion(),
                Map.of(identity.protocolVersion(), protocolSpecUrl(identity.protocolVersion())),
                identity.signingKeyId(),
                signingKeys,
                capabilities
        );
    }

    public AgentProfile withSigningKeys(List<PublicSigningKey> signingKeys) {
        return new AgentProfile(profileUrl, protocolVersion, supportedVersions, signingKeyId, signingKeys, capabilities);
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
