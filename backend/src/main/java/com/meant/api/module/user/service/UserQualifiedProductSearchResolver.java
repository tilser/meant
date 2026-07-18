package com.meant.api.module.user.service;

import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Resolves only immutable READY qualification plans into executable catalog constraints. */
@Service
@Validated
@RequiredArgsConstructor
public class UserQualifiedProductSearchResolver {

    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchQualificationPlanMapper planMapper;
    private final UserProductSearchProperties searchProperties;

    public UserQualifiedProductSearchInput resolve(
            @NotNull UUID userId,
            @NotNull UUID qualificationId
    ) {
        var snapshot = persistenceService.getReady(
                new GetUserProductSearchQualificationQuery(userId, qualificationId));
        if (!snapshot.plan().currentSchema()) {
            throw UserException.notFound(
                    "Product-search qualification uses an outdated plan; start a new qualification");
        }
        if (snapshot.updatedAt().plus(searchProperties.cacheTtl()).isBefore(Instant.now())) {
            throw UserException.notFound("Product-search qualification expired");
        }
        return new UserQualifiedProductSearchInput(
                snapshot.qualificationId(),
                snapshot.conversationId(),
                snapshot.merchantId(),
                snapshot.plan().effectiveQuery(),
                planMapper.map(snapshot.plan())
        );
    }
}
