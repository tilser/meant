package com.meant.api.module.order.repository;

import com.meant.api.module.order.entity.MerchantOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantOrderRepository extends JpaRepository<MerchantOrder, UUID> {

    @EntityGraph(attributePaths = "lines")
    List<MerchantOrder> findByUserIdOrderByPlacedAtDescCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "lines")
    Optional<MerchantOrder> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "lines")
    Optional<MerchantOrder> findByMerchantIdAndRemoteOrderIdHash(UUID merchantId, String remoteOrderIdHash);
}
