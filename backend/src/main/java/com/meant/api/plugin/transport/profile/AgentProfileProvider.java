package com.meant.api.plugin.transport.profile;

import com.meant.api.plugin.signing.SigningKeyProvider;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentProfileProvider {

    private final AgentProfile profile;

    @Autowired
    public AgentProfileProvider(
            CapabilityRegistry capabilityRegistry,
            AgentIdentity agentIdentity,
            SigningKeyProvider signingKeyProvider
    ) {
        this.profile = capabilityRegistry.agentProfile(agentIdentity)
                .withSigningKeys(signingKeyProvider.publicKeys());
    }

    AgentProfileProvider(CapabilityRegistry capabilityRegistry, AgentIdentity agentIdentity) {
        this(capabilityRegistry, agentIdentity, SigningKeyProvider.empty());
    }

    public AgentProfile profile() {
        return profile;
    }
}
