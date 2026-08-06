package com.meant.api.module.user.service;

import com.meant.api.module.user.service.command.TransferUserCanonicalProductAccessCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Transfers only server-owned product anchors explicitly present in a claimed guest conversation. */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class UserCanonicalProductAccessTransferService {
    private final UserCanonicalProductReferencePersistenceService referencePersistenceService;
    private final UserCanonicalProductSessionStore sessionStore;

    @Transactional
    public void transfer(@NotNull @Valid TransferUserCanonicalProductAccessCommand command) {
        List<String> canonicalProductKeys = command.canonicalProductKeys().stream()
                .filter(key -> key != null && !key.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        if (canonicalProductKeys.isEmpty() || command.sourceUserId().equals(command.targetUserId())) {
            return;
        }

        int durableReferenceCount = referencePersistenceService.copyReferences(
                command.sourceUserId(), command.targetUserId(), canonicalProductKeys);
        int sessionProductCount = sessionStore.copyAccess(
                command.sourceUserId(), command.targetUserId(), canonicalProductKeys);
        log.info(
                "Canonical product access transferred. sourceUserId={}, targetUserId={}, "
                        + "canonicalProductCount={}, durableReferenceCount={}, sessionProductCount={}",
                command.sourceUserId(),
                command.targetUserId(),
                canonicalProductKeys.size(),
                durableReferenceCount,
                sessionProductCount
        );
    }
}
