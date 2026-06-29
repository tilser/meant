package com.meant.api.plugin.transport.profile;

import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import org.springframework.stereotype.Component;

@Component
public class AgentProfileProvider {

    private final AgentProfile profile;

    public AgentProfileProvider(CapabilityRegistry capabilityRegistry, AgentIdentity agentIdentity) {
        this.profile = capabilityRegistry.agentProfile(agentIdentity);
    }

    public AgentProfile profile() {
        return profile;
    }
}
