package com.meant.api.module.review.repository;

import com.meant.api.module.review.entity.ReviewProvider;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewProviderRepository extends JpaRepository<ReviewProvider, UUID> {

    Optional<ReviewProvider> findByMerchantId(UUID merchantId);

    List<ReviewProvider> findByMerchantIdIn(Collection<UUID> merchantIds);
}
