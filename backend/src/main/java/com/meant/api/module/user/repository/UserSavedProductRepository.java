package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserSavedProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSavedProductRepository extends JpaRepository<UserSavedProduct, UUID> {

    List<UserSavedProduct> findByUserIdAndReferenceVerifiedAtIsNotNullOrderByCreatedAtDescIdDesc(
            UUID userId,
            Pageable pageable
    );

    Optional<UserSavedProduct> findByUserIdAndProductKey(UUID userId, String productKey);

    Optional<UserSavedProduct> findByUserIdAndProductKeyAndReferenceVerifiedAtIsNotNull(
            UUID userId,
            String productKey
    );

    Optional<UserSavedProduct> findByIdAndUserIdAndReferenceVerifiedAtIsNotNull(UUID id, UUID userId);

    long countByUserIdAndReferenceVerifiedAtIsNotNull(UUID userId);

    long deleteByUserIdAndProductKey(UUID userId, String productKey);
}
