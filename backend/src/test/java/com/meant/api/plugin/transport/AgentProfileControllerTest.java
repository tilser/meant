package com.meant.api.plugin.transport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentProfileControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentIdentity identity = new AgentIdentity(
                URI.create("https://agent.example/.well-known/ucp-agent.json"),
                "2026-04-08",
                "agent-key-1"
        );
        AgentProfileProvider provider = new AgentProfileProvider(new CapabilityRegistry(List.of()), identity);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentProfileController(provider)).build();
    }

    @Test
    void servesGeneratedAgentProfile() throws Exception {
        mockMvc.perform(get("/.well-known/ucp-agent.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_url").value("https://agent.example/.well-known/ucp-agent.json"))
                .andExpect(jsonPath("$.protocol_version").value("2026-04-08"))
                .andExpect(jsonPath("$.supported_versions['2026-04-08']").value("https://ucp.dev/2026-04-08"))
                .andExpect(jsonPath("$.signing_key_id").value("agent-key-1"));
    }
}
