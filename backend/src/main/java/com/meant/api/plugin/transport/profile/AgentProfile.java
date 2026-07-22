package com.meant.api.plugin.transport.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.signing.JsonWebKey;
import com.meant.api.plugin.signing.PublicSigningKey;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentProfile(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UcpProfile ucp,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<JsonWebKey> keys
) {

    private static final String SHOPPING_SERVICE = "dev.ucp.shopping";

    public AgentProfile {
        Objects.requireNonNull(ucp, "ucp must not be null");
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    public static AgentProfile from(AgentIdentity identity, List<CapabilityAdvertisement> capabilities) {
        return from(identity, capabilities, List.of());
    }

    public static AgentProfile from(
            AgentIdentity identity,
            List<CapabilityAdvertisement> capabilities,
            List<PublicSigningKey> signingKeys
    ) {
        Objects.requireNonNull(identity, "identity must not be null");
        String protocolVersion = requireText(identity.protocolVersion(), "protocolVersion");
        return new AgentProfile(
                new UcpProfile(
                        protocolVersion,
                        shoppingServices(protocolVersion),
                        capabilityProfiles(protocolVersion, capabilities),
                        Map.of()
                ),
                publicJwks(signingKeys)
        );
    }

    public AgentProfile withSigningKeys(List<PublicSigningKey> signingKeys) {
        return new AgentProfile(ucp, publicJwks(signingKeys));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record UcpProfile(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String version,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Map<String, List<ServiceProfile>> services,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Map<String, List<CapabilityProfile>> capabilities,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonInclude(JsonInclude.Include.ALWAYS)
            @JsonProperty("payment_handlers")
            Map<String, List<PaymentHandlerProfile>> paymentHandlers
    ) {

        public UcpProfile {
            version = requireText(version, "version");
            services = immutableListMap(services);
            capabilities = immutableListMap(capabilities);
            paymentHandlers = immutableListMap(paymentHandlers);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ServiceProfile(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String version,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            URI spec,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String transport,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            URI schema
    ) {

        public ServiceProfile {
            version = requireText(version, "version");
            Objects.requireNonNull(spec, "spec must not be null");
            transport = requireText(transport, "transport");
            Objects.requireNonNull(schema, "schema must not be null");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CapabilityProfile(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String version,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            URI spec,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            URI schema,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("extends")
            List<CapabilityId> extendsCapabilities,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            JsonNode config
    ) {

        public CapabilityProfile {
            version = requireText(version, "version");
            Objects.requireNonNull(spec, "spec must not be null");
            Objects.requireNonNull(schema, "schema must not be null");
            extendsCapabilities = extendsCapabilities == null ? List.of() : List.copyOf(extendsCapabilities);
            config = config == null ? emptyConfig() : requireObjectConfig(config).deepCopy();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PaymentHandlerProfile(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String version,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            URI spec,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            URI schema,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            JsonNode config
    ) {

        public PaymentHandlerProfile {
            id = requireText(id, "id");
            version = requireText(version, "version");
            Objects.requireNonNull(spec, "spec must not be null");
            Objects.requireNonNull(schema, "schema must not be null");
            config = config == null ? emptyConfig() : requireObjectConfig(config).deepCopy();
        }
    }

    private static Map<String, List<ServiceProfile>> shoppingServices(String protocolVersion) {
        return Map.of(SHOPPING_SERVICE, List.of(new ServiceProfile(
                protocolVersion,
                URI.create("https://ucp.dev/%s/specification/overview".formatted(protocolVersion)),
                "mcp",
                URI.create("https://ucp.dev/%s/services/shopping/mcp.openrpc.json".formatted(protocolVersion))
        )));
    }

    private static Map<String, List<CapabilityProfile>> capabilityProfiles(
            String protocolVersion,
            List<CapabilityAdvertisement> advertisements
    ) {
        if (advertisements == null || advertisements.isEmpty()) {
            return Map.of();
        }
        Map<String, List<CapabilityProfile>> values = new LinkedHashMap<>();
        advertisements.stream()
                .filter(Objects::nonNull)
                .filter(advertisement -> supportsProtocol(advertisement, protocolVersion))
                .forEach(advertisement -> {
                    CapabilityProfile profile = new CapabilityProfile(
                            advertisement.version(),
                            advertisement.spec(),
                            advertisement.schema(),
                            advertisement.extendsCapabilities(),
                            advertisement.config()
                    );
                    List<CapabilityProfile> versions = values.computeIfAbsent(
                            advertisement.id().value(),
                            ignored -> new ArrayList<>()
                    );
                    if (!versions.contains(profile)) {
                        versions.add(profile);
                    }
                });
        return immutableListMap(values);
    }

    private static boolean supportsProtocol(CapabilityAdvertisement advertisement, String protocolVersion) {
        CapabilityAdvertisement.ProtocolVersions versions = advertisement.protocolVersions();
        return protocolVersion.compareTo(versions.min()) >= 0 && protocolVersion.compareTo(versions.max()) <= 0;
    }

    private static List<JsonWebKey> publicJwks(List<PublicSigningKey> signingKeys) {
        if (signingKeys == null || signingKeys.isEmpty()) {
            return List.of();
        }
        return signingKeys.stream()
                .filter(Objects::nonNull)
                .map(PublicSigningKey::jwk)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static <T> Map<String, List<T>> immutableListMap(Map<String, List<T>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<T>> copy = new LinkedHashMap<>();
        values.forEach((key, entries) -> copy.put(key, entries == null ? List.of() : List.copyOf(entries)));
        return Collections.unmodifiableMap(copy);
    }

    private static JsonNode requireObjectConfig(JsonNode config) {
        if (!config.isObject()) {
            throw new IllegalArgumentException("config must be a JSON object");
        }
        return config;
    }

    private static JsonNode emptyConfig() {
        return JsonNodeFactory.instance.objectNode();
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
