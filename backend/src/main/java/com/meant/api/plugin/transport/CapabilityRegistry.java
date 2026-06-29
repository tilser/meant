package com.meant.api.plugin.transport;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.UcpCapability;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CapabilityRegistry {

    private final List<UcpCapability<?, ?>> capabilities;
    private final Map<String, UcpCapability<?, ?>> toolToCapability;

    public CapabilityRegistry(List<UcpCapability<?, ?>> capabilities) {
        this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        this.toolToCapability = buildToolMap(this.capabilities);
    }

    public UcpCapability<?, ?> capabilityForTool(String toolName) {
        return findCapabilityForTool(toolName)
                .orElseThrow(() -> new UnknownUcpToolException(toolName));
    }

    public Optional<UcpCapability<?, ?>> findCapabilityForTool(String toolName) {
        return Optional.ofNullable(toolToCapability.get(normalizeToolName(toolName)));
    }

    public AgentProfile agentProfile(AgentIdentity identity) {
        List<CapabilityAdvertisement> advertisements = capabilities.stream()
                .flatMap(capability -> capability.advertisements().stream())
                .sorted(Comparator
                        .comparing((CapabilityAdvertisement advertisement) -> advertisement.id().value())
                        .thenComparing(CapabilityAdvertisement::version))
                .toList();
        return AgentProfile.from(identity, advertisements);
    }

    public List<UcpCapability<?, ?>> capabilities() {
        return capabilities;
    }

    public Map<String, UcpCapability<?, ?>> toolToCapability() {
        return toolToCapability;
    }

    private Map<String, UcpCapability<?, ?>> buildToolMap(List<UcpCapability<?, ?>> capabilities) {
        Map<String, UcpCapability<?, ?>> tools = new LinkedHashMap<>();
        for (UcpCapability<?, ?> capability : capabilities) {
            for (String toolName : capability.toolNames()) {
                String normalizedToolName = normalizeToolName(toolName);
                UcpCapability<?, ?> existingCapability = tools.putIfAbsent(normalizedToolName, capability);
                if (existingCapability != null) {
                    throw new DuplicateUcpToolException(
                            normalizedToolName,
                            existingCapability.id(),
                            capability.id()
                    );
                }
            }
        }
        return Collections.unmodifiableMap(tools);
    }

    private String normalizeToolName(String toolName) {
        return toolName == null ? "" : toolName.trim();
    }
}
