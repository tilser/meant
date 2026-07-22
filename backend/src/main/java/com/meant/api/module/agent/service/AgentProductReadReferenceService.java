package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentInventoryArtifactKind;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentInventorySearchArtifact;
import com.meant.api.module.agent.service.dto.AgentInventorySelectedItemArtifact;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.tool.AgentProductReadToolException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentProductReadReferenceService {

    private static final Set<AgentArtifactType> OFFER_ISSUING_TYPES = Set.of(
            AgentArtifactType.PRODUCT,
            AgentArtifactType.SAVED_PRODUCT,
            AgentArtifactType.OFFER,
            AgentArtifactType.CART_LINE
    );

    private final AgentArtifactReferenceRepository repository;
    private final AgentJsonSupport json;

    public AgentArtifactReference requireProduct(AgentToolExecutionContext context, String canonicalProductKey) {
        if (canonicalProductKey == null || canonicalProductKey.isBlank()) {
            throw AgentProductReadToolException.invalid("A product reference is required.");
        }
        AgentArtifactReference reference = requireStableKey(context, canonicalProductKey.trim());
        if ((reference.getArtifactType() != AgentArtifactType.PRODUCT
                && reference.getArtifactType() != AgentArtifactType.SAVED_PRODUCT)
                || !canonicalProductKey.trim().equals(reference.getCanonicalProductKey())) {
            throw AgentException.notFound();
        }
        return reference;
    }

    public AgentArtifactReference requireInventoryItem(AgentToolExecutionContext context, UUID inventoryItemId) {
        if (inventoryItemId == null) {
            throw AgentProductReadToolException.invalid("An inventory item reference is required.");
        }
        AgentArtifactReference reference = requireStableKey(context, "inventory:" + inventoryItemId);
        if (reference.getArtifactType() != AgentArtifactType.INVENTORY_ITEM
                || !inventoryItemId.equals(reference.getInventoryItemId())) {
            throw AgentException.notFound();
        }
        return reference;
    }

    /**
     * Requires a server-issued inventory result that is unambiguous and complete. A user-selected
     * item can be made unambiguous by loading it with {@code get_inventory_item} first.
     */
    public AgentArtifactReference requireSoleInventoryItem(
            AgentToolExecutionContext context,
            UUID inventoryItemId
    ) {
        AgentArtifactReference reference = requireInventoryItem(context, inventoryItemId);
        List<AgentArtifactReference> resultSet = repository
                .findByConversationIdAndMessageIdOrderByOrdinalAsc(
                        context.conversationId(), reference.getMessageId())
                .stream()
                .filter(candidate -> candidate.getArtifactType() == AgentArtifactType.INVENTORY_ITEM)
                .toList();
        boolean exactSingleton = resultSet.size() == 1
                && inventoryItemId.equals(resultSet.getFirst().getInventoryItemId());
        AgentInventorySearchArtifact search = json.readArtifact(
                        reference.getPayloadJson(), AgentInventorySearchArtifact.class)
                .filter(candidate -> candidate.kind() == AgentInventoryArtifactKind.SEARCH_RESULT)
                .filter(candidate -> candidate.item() != null)
                .orElse(null);
        boolean selectedItem = json.readArtifact(
                        reference.getPayloadJson(), AgentInventorySelectedItemArtifact.class)
                .filter(candidate -> candidate.kind() == AgentInventoryArtifactKind.SELECTED_ITEM)
                .filter(candidate -> candidate.item() != null)
                .isPresent();
        boolean completeSearch = search != null && !search.hasMore() && !search.scanTruncated();
        if (!exactSingleton || (!completeSearch && !selectedItem)) {
            throw AgentProductReadToolException.invalid(
                    "The inventory match is ambiguous. Ask the user to choose an item, then load "
                            + "that exact item with get_inventory_item before finding similar products.");
        }
        return reference;
    }

    public AgentArtifactReference requireOrder(AgentToolExecutionContext context, UUID orderId) {
        if (orderId == null) {
            throw AgentProductReadToolException.invalid("An order reference is required.");
        }
        AgentArtifactReference reference = requireStableKey(context, "order:" + orderId);
        if (reference.getArtifactType() != AgentArtifactType.ORDER) {
            throw AgentException.notFound();
        }
        return reference;
    }

    public AgentArtifactReference requireOffer(AgentToolExecutionContext context, String offerKey) {
        String normalized = requiredReference(offerKey, "An exact offer reference is required.");
        requireContext(context);
        AgentArtifactReference reference = repository
                .findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(context.conversationId(), normalized)
                .filter(candidate -> normalized.equals(candidate.getOfferKey()))
                .or(() -> repository.findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
                        context.conversationId(), normalized))
                .filter(candidate -> OFFER_ISSUING_TYPES.contains(candidate.getArtifactType()))
                .filter(candidate -> normalized.equals(candidate.getOfferKey()))
                .orElseThrow(AgentException::notFound);
        return reference;
    }

    public AgentArtifactReference requireOffer(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        String canonical = requiredReference(canonicalProductKey, "A canonical product reference is required.");
        AgentArtifactReference reference = requireOffer(context, offerKey);
        if (!canonical.equals(reference.getCanonicalProductKey())) {
            throw AgentException.notFound();
        }
        return reference;
    }

    public AgentArtifactReference requireCart(AgentToolExecutionContext context, UUID cartId) {
        if (cartId == null) {
            throw AgentProductReadToolException.invalid("A cart reference is required.");
        }
        AgentArtifactReference reference = requireStableKey(context, "cart:" + cartId);
        if (reference.getArtifactType() != AgentArtifactType.CART || !cartId.equals(reference.getCartId())) {
            throw AgentException.notFound();
        }
        return reference;
    }

    public AgentArtifactReference requireCartLine(
            AgentToolExecutionContext context,
            UUID cartId,
            UUID cartLineId
    ) {
        requireCart(context, cartId);
        if (cartLineId == null) {
            throw AgentProductReadToolException.invalid("A cart-line reference is required.");
        }
        AgentArtifactReference reference = requireStableKey(context, "cart-line:" + cartLineId);
        if (reference.getArtifactType() != AgentArtifactType.CART_LINE
                || !cartId.equals(reference.getCartId())
                || !cartLineId.equals(reference.getCartLineId())) {
            throw AgentException.notFound();
        }
        return reference;
    }

    private AgentArtifactReference requireStableKey(AgentToolExecutionContext context, String stableKey) {
        requireContext(context);
        return repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                        context.conversationId(), stableKey)
                .orElseThrow(AgentException::notFound);
    }

    private void requireContext(AgentToolExecutionContext context) {
        if (context == null || context.userId() == null || context.conversationId() == null) {
            throw new IllegalArgumentException("An authenticated conversation-scoped tool context is required");
        }
    }

    private String requiredReference(String value, String message) {
        if (value == null || value.isBlank()) {
            throw AgentProductReadToolException.invalid(message);
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw AgentProductReadToolException.invalid("The reference is too long.");
        }
        return normalized;
    }
}
