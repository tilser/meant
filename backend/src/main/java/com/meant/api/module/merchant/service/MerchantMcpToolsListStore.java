package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import java.util.Optional;
import java.util.UUID;

public interface MerchantMcpToolsListStore {

    Optional<MerchantMcpToolsList> find(UUID merchantId, String agentProfileHash);

    void save(MerchantMcpToolsList toolsList);
}
