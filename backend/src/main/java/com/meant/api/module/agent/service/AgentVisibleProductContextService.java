package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.command.VisibleProductContextCommand;
import com.meant.api.module.agent.service.dto.AgentTurnContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AgentVisibleProductContextService {

    private static final Set<AgentArtifactType> PRODUCT_TYPES = Set.of(
            AgentArtifactType.PRODUCT,
            AgentArtifactType.SAVED_PRODUCT
    );

    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentJsonSupport json;
    private final ObjectMapper objectMapper;

    public AgentVisibleProductContext resolve(UUID conversationId, VisibleProductContextCommand command) {
        if (command == null) {
            return null;
        }
        List<AgentArtifactReference> issued = artifactRepository
                .findByConversationIdAndMessageIdOrderByOrdinalAsc(
                        conversationId,
                        command.sourceMessageId()
                );
        LinkedHashMap<String, AgentArtifactReference> productsByKey = new LinkedHashMap<>();
        issued.stream()
                .filter(reference -> PRODUCT_TYPES.contains(reference.getArtifactType()))
                .filter(reference -> present(reference.getCanonicalProductKey()))
                .forEach(reference -> productsByKey.putIfAbsent(reference.getCanonicalProductKey(), reference));

        List<AgentVisibleProductReference> visible = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int priorResultOrdinal = 0;
        for (String requested : command.orderedCanonicalProductKeys()) {
            String canonicalProductKey = requested == null ? null : requested.trim();
            AgentArtifactReference reference = productsByKey.get(canonicalProductKey);
            if (!present(canonicalProductKey)
                    || reference == null
                    || !seen.add(canonicalProductKey)
                    || reference.getOrdinal() <= priorResultOrdinal) {
                throw invalidContext();
            }
            priorResultOrdinal = reference.getOrdinal();
            visible.add(new AgentVisibleProductReference(
                    visible.size() + 1,
                    reference.getOrdinal(),
                    canonicalProductKey,
                    reference.getOfferKey(),
                    reference.getLabel()
            ));
        }
        if (visible.isEmpty()) {
            throw invalidContext();
        }
        return new AgentVisibleProductContext(command.sourceMessageId(), visible);
    }

    public String serialize(AgentVisibleProductContext context) {
        return context == null ? null : json.writeArtifact(new AgentTurnContext(context));
    }

    public Optional<AgentVisibleProductContext> deserialize(String contentJson) {
        if (!present(contentJson)) {
            return Optional.empty();
        }
        try {
            AgentTurnContext context = objectMapper.readValue(contentJson, AgentTurnContext.class);
            AgentVisibleProductContext visible = context == null ? null : context.visibleProducts();
            if (visible == null || visible.sourceMessageId() == null || visible.products().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(visible);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private AgentException invalidContext() {
        return new AgentException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Visible product context does not match the current conversation."
        );
    }
}
