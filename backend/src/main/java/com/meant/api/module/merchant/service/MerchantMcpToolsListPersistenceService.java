package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import com.meant.api.module.merchant.repository.MerchantMcpToolsListRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantMcpToolsListPersistenceService implements MerchantMcpToolsListStore {

    private final MerchantMcpToolsListRepository repository;

    @Override
    public Optional<MerchantMcpToolsList> find(UUID merchantId, String agentProfileHash) {
        return repository.findByMerchantIdAndAgentProfileHash(merchantId, agentProfileHash);
    }

    @Override
    public void save(MerchantMcpToolsList toolsList) {
        repository.save(toolsList);
    }
}
