package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProductSearchQualificationRepository
        extends JpaRepository<UserProductSearchQualification, UUID> {

    Optional<UserProductSearchQualification> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserProductSearchQualification>
            findFirstByUserIdAndConversationIdAndMerchantIdAndStatusOrderByUpdatedAtDesc(
                    UUID userId,
                    UUID conversationId,
                    UUID merchantId,
                    UserProductSearchQualificationStatus status
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select qualification from UserProductSearchQualification qualification where qualification.id = :id")
    Optional<UserProductSearchQualification> findByIdForUpdate(@Param("id") UUID id);
}
