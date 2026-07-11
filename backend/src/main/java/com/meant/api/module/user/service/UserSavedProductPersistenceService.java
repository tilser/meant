package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.entity.UserSavedProduct.DurableReferenceSnapshot;
import com.meant.api.module.user.entity.UserSavedProduct.SavedProductSnapshot;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Short write boundary invoked only after remote rehydration has completed. */
@Service
@RequiredArgsConstructor
public class UserSavedProductPersistenceService {
    private final UserSavedProductRepository repository;
    private final UserCollectionProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserSavedProduct save(
            SaveUserProductCommand command,
            CatalogProductReference reference,
            String retentionPolicyKey,
            Instant now
    ) {
        UserSavedProduct entity = repository.findByUserIdAndProductKey(command.userId(), command.productKey())
                .map(existing -> existing.replaceSnapshot(displayHint(command), now))
                .orElseGet(() -> {
                    if (repository.countByUserId(command.userId()) >= properties.savedProducts().quota()) {
                        throw new UserException("Saved product quota exceeded for user " + command.userId());
                    }
                    return UserSavedProduct.create(command.userId(), displayHint(command), now);
                });
        entity.replaceReference(referenceSnapshot(reference, retentionPolicyKey), now);
        return repository.save(entity);
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

    private SavedProductSnapshot displayHint(SaveUserProductCommand command) {
        return new SavedProductSnapshot(
                command.productKey(),
                null,
                command.name(),
                command.brand(),
                command.category(),
                command.tone(),
                null,
                null,
                command.remote(),
                command.matchScore(),
                null,
                command.merchantCount(),
                json(safeList(command.satisfies())),
                json(safeList(command.misses())),
                command.note(),
                json(safeList(command.pros())),
                json(safeList(command.cons())),
                command.review().score(),
                command.review().count(),
                command.review().insight(),
                null,
                command.needs(),
                json(safeList(command.provides()))
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new UserException("Could not serialize saved product identifiers", exception);
        }
    }
}
