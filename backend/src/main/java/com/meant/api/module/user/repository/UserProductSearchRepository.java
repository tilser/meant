package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface UserProductSearchRepository extends JpaRepository<UserProductSearch, UUID> {

    Optional<UserProductSearch> findFirstByUserIdAndNormalizedQueryAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            Instant now
    );

    Optional<UserProductSearch> findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String searchVersion
    );

    List<UserProductSearch> findByUserIdAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc(
            UUID userId,
            String profileHash,
            String searchVersion,
            Instant now,
            Pageable pageable
    );
}
