package com.meant.api.plugin.transport.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentProfileHashService implements AgentProfileHashProvider {

    private final String currentHash;

    public AgentProfileHashService(AgentProfileProvider agentProfileProvider, ObjectMapper objectMapper) {
        this.currentHash = hashProfile(agentProfileProvider.profile(), objectMapper);
    }

    public String currentHash() {
        return currentHash;
    }

    private String hashProfile(AgentProfile agentProfile, ObjectMapper objectMapper) {
        try {
            return sha256(objectMapper.writeValueAsString(agentProfile));
        } catch (JacksonException exception) {
            throw new IllegalStateException("UCP agent profile could not be serialized for hashing", exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
