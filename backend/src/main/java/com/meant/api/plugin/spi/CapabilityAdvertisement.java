package com.meant.api.plugin.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CapabilityAdvertisement(
        CapabilityId id,
        String version,
        List<String> tools,
        boolean required,
        @JsonProperty("protocol_versions") ProtocolVersions protocolVersions,
        Requirements requires,
        URI spec,
        URI schema,
        @JsonProperty("extends") List<CapabilityId> extendsCapabilities,
        Map<String, Object> config
) {

    public CapabilityAdvertisement {
        Objects.requireNonNull(id, "id must not be null");
        version = requireText(version, "version");
        tools = normalizedTools(tools);
        protocolVersions = Objects.requireNonNull(protocolVersions, "protocolVersions must not be null");
        requires = requires == null ? Requirements.none() : requires;
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        extendsCapabilities = extendsCapabilities == null ? List.of() : List.copyOf(extendsCapabilities);
        config = config == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(config));
    }

    public CapabilityAdvertisement(
            CapabilityId id,
            String version,
            List<String> tools,
            boolean required,
            ProtocolVersions protocolVersions,
            Requirements requires,
            URI spec,
            URI schema
    ) {
        this(id, version, tools, required, protocolVersions, requires, spec, schema, List.of(), Map.of());
    }

    public static CapabilityAdvertisement required(
            CapabilityId id,
            String version,
            List<String> tools,
            String protocolVersion,
            URI spec,
            URI schema
    ) {
        return new CapabilityAdvertisement(
                id,
                version,
                tools,
                true,
                ProtocolVersions.exact(protocolVersion),
                Requirements.none(),
                spec,
                schema,
                List.of(),
                Map.of()
        );
    }

    public static CapabilityAdvertisement optional(
            CapabilityId id,
            String version,
            List<String> tools,
            String protocolVersion,
            URI spec,
            URI schema
    ) {
        return new CapabilityAdvertisement(
                id,
                version,
                tools,
                false,
                ProtocolVersions.exact(protocolVersion),
                Requirements.none(),
                spec,
                schema,
                List.of(),
                Map.of()
        );
    }

    public record ProtocolVersions(
            String min,
            String max
    ) {

        public ProtocolVersions {
            min = requireText(min, "min");
            max = requireText(max, "max");
        }

        public static ProtocolVersions exact(String protocolVersion) {
            return new ProtocolVersions(protocolVersion, protocolVersion);
        }
    }

    public record Requirements(
            @JsonProperty("required_capabilities") List<CapabilityId> requiredCapabilities,
            @JsonProperty("optional_capabilities") List<CapabilityId> optionalCapabilities
    ) {

        public Requirements {
            requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
            optionalCapabilities = optionalCapabilities == null ? List.of() : List.copyOf(optionalCapabilities);
        }

        public static Requirements none() {
            return new Requirements(List.of(), List.of());
        }
    }

    private static List<String> normalizedTools(List<String> tools) {
        return tools == null
                ? List.of()
                : List.copyOf(tools.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(tool -> !tool.isBlank())
                        .distinct()
                        .toList());
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
