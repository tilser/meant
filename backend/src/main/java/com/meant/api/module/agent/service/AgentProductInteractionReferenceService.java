package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentProductInteractionReference;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentProductInteractionReferenceService {

    static final String STATE_STABLE_KEY_PREFIX = "product-state:";

    private final AgentArtifactReferenceRepository artifactRepository;

    public AgentProductInteractionReference resolve(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String requestedOfferKey
    ) {
        requireContext(context);
        String canonicalKey = requiredText(canonicalProductKey, "A product reference is required.");
        AgentArtifactReference product = productReference(context, canonicalKey)
                .orElseThrow(AgentException::notFound);
        String offerKey = optionalText(requestedOfferKey);
        if (offerKey == null) {
            offerKey = product.getOfferKey();
        } else if (!offerKey.equals(product.getOfferKey()) && !isIssuedOffer(context, canonicalKey, offerKey)) {
            throw AgentException.notFound();
        }
        return new AgentProductInteractionReference(canonicalKey, offerKey, product.getLabel());
    }

    private Optional<AgentArtifactReference> productReference(
            AgentToolExecutionContext context,
            String canonicalProductKey
    ) {
        Optional<AgentArtifactReference> product = artifactRepository
                .findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                        context.conversationId(), canonicalProductKey)
                .filter(reference -> validProduct(reference, canonicalProductKey));
        if (product.isPresent()) {
            return product;
        }
        return artifactRepository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                        context.conversationId(), STATE_STABLE_KEY_PREFIX + canonicalProductKey)
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.PRODUCT_STATE)
                .filter(reference -> canonicalProductKey.equals(reference.getCanonicalProductKey()));
    }

    private boolean validProduct(AgentArtifactReference reference, String canonicalProductKey) {
        return (reference.getArtifactType() == AgentArtifactType.PRODUCT
                || reference.getArtifactType() == AgentArtifactType.SAVED_PRODUCT)
                && canonicalProductKey.equals(reference.getCanonicalProductKey());
    }

    private boolean isIssuedOffer(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        return artifactRepository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                        context.conversationId(), offerKey)
                .filter(reference -> reference.getArtifactType() == AgentArtifactType.OFFER)
                .filter(reference -> offerKey.equals(reference.getOfferKey()))
                .filter(reference -> canonicalProductKey.equals(reference.getCanonicalProductKey()))
                .isPresent();
    }

    private void requireContext(AgentToolExecutionContext context) {
        if (context == null || context.userId() == null || context.conversationId() == null) {
            throw new IllegalArgumentException("An authenticated conversation-scoped tool context is required");
        }
    }

    private String requiredText(String value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw AgentProductReadToolException.invalid(message);
        }
        if (normalized.length() > 200) {
            throw AgentProductReadToolException.invalid("The product reference is too long.");
        }
        return normalized;
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw AgentProductReadToolException.invalid("The offer reference is too long.");
        }
        return normalized;
    }
}
