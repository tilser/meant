package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantMcpToolsListRepository extends JpaRepository<MerchantMcpToolsList, UUID> {

    Optional<MerchantMcpToolsList> findByMerchantIdAndAgentProfileHash(UUID merchantId, String agentProfileHash);
}
