package com.meant.api.plugin.transport.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andExpect(jsonPath("$.ucp.version").value("2026-04-08"))
                .andExpect(jsonPath("$.ucp.services['dev.ucp.shopping'][0].version").value("2026-04-08"))
                .andExpect(jsonPath("$.ucp.services['dev.ucp.shopping'][0].transport").value("mcp"))
                .andExpect(jsonPath("$.ucp.payment_handlers").isMap())
                .andExpect(jsonPath("$.profile_url").doesNotExist())
                .andExpect(jsonPath("$.protocol_version").doesNotExist())
                .andExpect(jsonPath("$.signing_key_id").doesNotExist());
    }
}
