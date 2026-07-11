package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.entity.UserSavedProduct.DurableReferenceSnapshot;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Short database boundaries for identifier-only saved interactions. */
@Service
@RequiredArgsConstructor
public class UserSavedProductPersistenceService {
    private final UserSavedProductRepository repository;
    private final UserCollectionProperties properties;
    private final UserTasteProfileService userTasteProfileService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<UserSavedProduct> findVerified(UUID userId, int candidateLimit) {
        return repository.findByUserIdAndReferenceVerifiedAtIsNotNullOrderByCreatedAtDescIdDesc(
                userId,
                PageRequest.of(0, candidateLimit)
        );
    }

    @Transactional
    public UserSavedProduct save(
            SaveUserProductCommand command,
            CatalogProductReference verifiedReference,
            String retentionPolicyKey,
            Instant now
    ) {
        if (!command.productKey().equals(verifiedReference.interactionKey())) {
            throw new UserException("Verified saved-product reference did not match the interaction key");
        }
        DurableReferenceSnapshot reference = referenceSnapshot(verifiedReference, retentionPolicyKey);
        UserSavedProduct entity = repository.findByUserIdAndProductKey(command.userId(), command.productKey())
                .map(existing -> existing.replaceReference(reference, now))
                .orElseGet(() -> {
                    if (repository.countByUserIdAndReferenceVerifiedAtIsNotNull(command.userId())
                            >= properties.savedProducts().quota()) {
                        throw new UserException("Saved product quota exceeded for user " + command.userId());
                    }
                    return UserSavedProduct.create(command.userId(), command.productKey(), reference, now);
                });
        UserSavedProduct saved = repository.save(entity);
        userTasteProfileService.recordSavedProduct(command.userId(), command, now);
        return saved;
    }

    private DurableReferenceSnapshot referenceSnapshot(CatalogProductReference reference, String policyKey) {
        return new DurableReferenceSnapshot(
                reference.discoverySource().provider().value(),
                reference.discoverySource().type().name(),
                reference.discoverySource().value(),
                reference.localMerchantId(),
                reference.localRouting() == null ? null : reference.localRouting().merchantIntegrationId(),
                reference.externalMerchantReference() == null ? null : reference.externalMerchantReference().value(),
                reference.externalProductReference().value(),
                reference.externalVariantReference() == null ? null : reference.externalVariantReference().value(),
                json(reference.selectedOptions()),
                policyKey
        );
    }

    private String json(List<ProductAttribute> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JacksonException exception) {
            throw new UserException("Could not serialize saved product identifiers", exception);
        }
    }
}
