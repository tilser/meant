package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearchQualificationRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProductSearchQualificationRequestRepository
        extends JpaRepository<UserProductSearchQualificationRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding
            from UserProductSearchQualificationRequest binding
            where binding.id = :requestId
            """)
    Optional<UserProductSearchQualificationRequest> findByIdForUpdate(
            @Param("requestId") UUID requestId
    );
}
