package com.meant.api.module.merchant.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListFetchResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListResult;
import com.meant.api.plugin.transport.AgentProfileHashProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MerchantMcpToolsListService {

    private final MerchantMcpToolsListStore store;
    private final MerchantMcpToolClient merchantMcpToolClient;
    private final AgentProfileHashProvider agentProfileHashProvider;
    private final Cache<CacheKey, MerchantMcpToolsListResult> cache;

    @Autowired
    public MerchantMcpToolsListService(
            MerchantMcpToolsListStore store,
            MerchantMcpToolClient merchantMcpToolClient,
            AgentProfileHashProvider agentProfileHashProvider,
            MerchantMcpToolProperties properties
    ) {
        this(
                store,
                merchantMcpToolClient,
                agentProfileHashProvider,
                properties,
                Ticker.systemTicker()
        );
    }

    MerchantMcpToolsListService(
            MerchantMcpToolsListStore store,
            MerchantMcpToolClient merchantMcpToolClient,
            AgentProfileHashProvider agentProfileHashProvider,
            MerchantMcpToolProperties properties,
            Ticker ticker
    ) {
        this.store = store;
        this.merchantMcpToolClient = merchantMcpToolClient;
        this.agentProfileHashProvider = agentProfileHashProvider;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.toolsListCacheTtl())
                .ticker(ticker)
                .build();
    }

    public MerchantMcpToolsListResult toolsList(Merchant merchant) {
        String agentProfileHash = agentProfileHashProvider.currentHash();
        CacheKey cacheKey = new CacheKey(merchant.getId(), agentProfileHash);
        return cache.get(cacheKey, key -> loadToolsList(merchant, key.agentProfileHash()));
    }

    private MerchantMcpToolsListResult loadToolsList(Merchant merchant, String agentProfileHash) {
        return store.find(merchant.getId(), agentProfileHash)
                .map(this::toResult)
                .orElseGet(() -> fetchAndPersistToolsList(merchant, agentProfileHash));
    }

    private MerchantMcpToolsListResult fetchAndPersistToolsList(Merchant merchant, String agentProfileHash) {
        MerchantMcpToolsListFetchResult fetchResult = merchantMcpToolClient.listTools(merchant);
        Instant now = Instant.now();
        String toolsListHash = sha256(fetchResult.toolsListRaw());

        MerchantMcpToolsList toolsList = store
                .find(merchant.getId(), agentProfileHash)
                .orElseGet(() -> MerchantMcpToolsList.builder()
                        .merchant(merchant)
                        .agentProfileHash(agentProfileHash)
                        .createdAt(now)
                        .build());
        toolsList.updateToolsList(
                fetchResult.endpoint(),
                fetchResult.toolsListRaw(),
                toolsListHash,
                now,
                now
        );
        store.save(toolsList);
        return toResult(toolsList);
    }

    private MerchantMcpToolsListResult toResult(MerchantMcpToolsList toolsList) {
        return new MerchantMcpToolsListResult(
                toolsList.getEndpoint(),
                toolsList.getToolsListRaw(),
                toolsList.getToolsListHash(),
                toolsList.getAgentProfileHash(),
                toolsList.getCapturedAt()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record CacheKey(
            UUID merchantId,
            String agentProfileHash
    ) {
    }
}
