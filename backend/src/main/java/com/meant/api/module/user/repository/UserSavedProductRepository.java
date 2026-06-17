package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserSavedProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSavedProductRepository extends JpaRepository<UserSavedProduct, UUID> {

    List<UserSavedProduct> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<UserSavedProduct> findByUserIdAndProductKey(UUID userId, String productKey);

    long deleteByUserIdAndProductKey(UUID userId, String productKey);
}
