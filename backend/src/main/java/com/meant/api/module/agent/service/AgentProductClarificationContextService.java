package com.meant.api.module.agent.service;

import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentTurnContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AgentProductClarificationContextService {

    private final AgentJsonSupport json;
    private final ObjectMapper objectMapper;

    public String serialize(AgentProductClarification clarification) {
        return clarification == null
                ? null
                : json.writeArtifact(new AgentTurnContext(null, clarification));
    }

    public Optional<AgentProductClarification> deserialize(String contentJson) {
        if (!present(contentJson)) {
            return Optional.empty();
        }
        try {
            AgentTurnContext context = objectMapper.readValue(contentJson, AgentTurnContext.class);
            AgentProductClarification clarification = context == null
                    ? null
                    : context.pendingProductClarification();
            return valid(clarification) ? Optional.of(clarification) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean valid(AgentProductClarification clarification) {
        if (clarification == null
                || !present(clarification.toolName())
                || !present(clarification.originalUserText())
                || clarification.products().isEmpty()) {
            return false;
        }
        Set<Integer> ordinals = new HashSet<>();
        Set<String> productKeys = new HashSet<>();
        for (AgentVisibleProductReference product : clarification.products()) {
            if (product == null
                    || product.visibleOrdinal() < 1
                    || !present(product.canonicalProductKey())
                    || !ordinals.add(product.visibleOrdinal())
                    || !productKeys.add(product.canonicalProductKey())) {
                return false;
            }
        }
        return true;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
