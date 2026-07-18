package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserDiscoverProductResultSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDiscoverProductResultSetRepository
        extends JpaRepository<UserDiscoverProductResultSet, UUID> {

    Optional<UserDiscoverProductResultSet> findByQualificationIdAndPageOffsetAndResultLimit(
            UUID qualificationId,
            int pageOffset,
            int resultLimit
    );

    Optional<UserDiscoverProductResultSet> findByIdAndUserIdAndConversationId(
            UUID id,
            UUID userId,
            UUID conversationId
    );
}
