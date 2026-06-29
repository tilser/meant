package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListFetchResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListResult;
import com.meant.api.plugin.transport.AgentProfileHashProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MerchantMcpToolsListServiceTest {

    @Test
    void usesCaffeineCacheAfterInitialMissAndPersistsFetchedToolsList() {
        Merchant merchant = merchant();
        InMemoryToolsListStore store = new InMemoryToolsListStore();
        FakeMcpToolClient client = new FakeMcpToolClient();
        client.enqueue(new MerchantMcpToolsListFetchResult(
                "https://merchant.example/api/mcp",
                "{\"tools\":[{\"name\":\"search_catalog\"}]}"
        ));
        MerchantMcpToolsListService service = service(
                store,
                client,
                new MutableProfileHashProvider("profile-a"),
                new MutableTicker(),
                Duration.ofHours(1)
        );

        MerchantMcpToolsListResult first = service.toolsList(merchant);
        MerchantMcpToolsListResult second = service.toolsList(merchant);

        assertThat(first.toolsListRaw()).contains("search_catalog");
        assertThat(second.toolsListRaw()).isEqualTo(first.toolsListRaw());
        assertThat(client.callCount()).isEqualTo(1);
        assertThat(store.savedCount()).isEqualTo(1);
        assertThat(store.find(merchant.getId(), "profile-a")).hasValueSatisfying(saved -> {
            assertThat(saved.getAgentProfileHash()).isEqualTo("profile-a");
            assertThat(saved.getToolsListRaw()).contains("search_catalog");
        });
    }

    @Test
    void loadsFromDatabaseWhenProfileHashMatchesAfterRestart() {
        Merchant merchant = merchant();
        InMemoryToolsListStore store = new InMemoryToolsListStore();
        store.save(storedToolsList(
                merchant,
                "profile-a",
                "{\"tools\":[{\"name\":\"get_product\"}]}",
                Instant.parse("2026-06-29T10:15:30Z")
        ));
        FakeMcpToolClient client = new FakeMcpToolClient();
        MerchantMcpToolsListService service = service(
                store,
                client,
                new MutableProfileHashProvider("profile-a"),
                new MutableTicker(),
                Duration.ofHours(1)
        );

        MerchantMcpToolsListResult result = service.toolsList(merchant);

        assertThat(result.toolsListRaw()).contains("get_product");
        assertThat(result.agentProfileHash()).isEqualTo("profile-a");
        assertThat(client.callCount()).isZero();
    }

    @Test
    void expiresCaffeineEntryAndReloadsAfterTtl() {
        Merchant merchant = merchant();
        InMemoryToolsListStore store = new InMemoryToolsListStore();
        FakeMcpToolClient client = new FakeMcpToolClient();
        MutableTicker ticker = new MutableTicker();
        client.enqueue(new MerchantMcpToolsListFetchResult(
                "https://merchant.example/api/mcp",
                "{\"tools\":[{\"name\":\"search_catalog\"}]}"
        ));
        client.enqueue(new MerchantMcpToolsListFetchResult(
                "https://merchant.example/api/mcp",
                "{\"tools\":[{\"name\":\"get_product\"}]}"
        ));
        MerchantMcpToolsListService service = service(
                store,
                client,
                new MutableProfileHashProvider("profile-a"),
                ticker,
                Duration.ofHours(1)
        );

        MerchantMcpToolsListResult first = service.toolsList(merchant);
        ticker.advance(Duration.ofMinutes(59));
        MerchantMcpToolsListResult cached = service.toolsList(merchant);
        ticker.advance(Duration.ofMinutes(2));
        store.clear();
        MerchantMcpToolsListResult reloaded = service.toolsList(merchant);

        assertThat(first.toolsListRaw()).contains("search_catalog");
        assertThat(cached.toolsListRaw()).contains("search_catalog");
        assertThat(reloaded.toolsListRaw()).contains("get_product");
        assertThat(client.callCount()).isEqualTo(2);
    }

    @Test
    void profileHashChangeInvalidatesCacheKeyAndFetchesFreshToolsList() {
        Merchant merchant = merchant();
        InMemoryToolsListStore store = new InMemoryToolsListStore();
        FakeMcpToolClient client = new FakeMcpToolClient();
        MutableProfileHashProvider profileHashProvider = new MutableProfileHashProvider("profile-a");
        client.enqueue(new MerchantMcpToolsListFetchResult(
                "https://merchant.example/api/mcp",
                "{\"tools\":[{\"name\":\"search_catalog\"}]}"
        ));
        client.enqueue(new MerchantMcpToolsListFetchResult(
                "https://merchant.example/api/mcp",
                "{\"tools\":[{\"name\":\"lookup_catalog\"}]}"
        ));
        MerchantMcpToolsListService service = service(
                store,
                client,
                profileHashProvider,
                new MutableTicker(),
                Duration.ofHours(1)
        );

        MerchantMcpToolsListResult first = service.toolsList(merchant);
        MerchantMcpToolsListResult cached = service.toolsList(merchant);
        profileHashProvider.setHash("profile-b");
        MerchantMcpToolsListResult changedProfile = service.toolsList(merchant);

        assertThat(first.toolsListRaw()).contains("search_catalog");
        assertThat(cached.toolsListRaw()).contains("search_catalog");
        assertThat(changedProfile.toolsListRaw()).contains("lookup_catalog");
        assertThat(changedProfile.agentProfileHash()).isEqualTo("profile-b");
        assertThat(client.callCount()).isEqualTo(2);
    }

    private MerchantMcpToolsListService service(
            MerchantMcpToolsListStore store,
            MerchantMcpToolClient client,
            AgentProfileHashProvider profileHashProvider,
            MutableTicker ticker,
            Duration ttl
    ) {
        return new MerchantMcpToolsListService(
                store,
                client,
                profileHashProvider,
                new MerchantMcpToolProperties(5000, 5000, 15000, ttl),
                ticker
        );
    }

    private Merchant merchant() {
        return Merchant.builder()
                .domain("merchant.example")
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .build();
    }

    private MerchantMcpToolsList storedToolsList(
            Merchant merchant,
            String agentProfileHash,
            String rawToolsList,
            Instant capturedAt
    ) {
        return MerchantMcpToolsList.builder()
                .merchant(merchant)
                .agentProfileHash(agentProfileHash)
                .endpoint("https://merchant.example/api/mcp")
                .toolsListRaw(rawToolsList)
                .toolsListHash("stored-hash")
                .capturedAt(capturedAt)
                .createdAt(capturedAt)
                .updatedAt(capturedAt)
                .build();
    }

    private static class InMemoryToolsListStore implements MerchantMcpToolsListStore {

        private final Map<Key, MerchantMcpToolsList> records = new LinkedHashMap<>();
        private int savedCount;

        @Override
        public Optional<MerchantMcpToolsList> find(UUID merchantId, String agentProfileHash) {
            return Optional.ofNullable(records.get(new Key(merchantId, agentProfileHash)));
        }

        @Override
        public void save(MerchantMcpToolsList toolsList) {
            records.put(new Key(toolsList.getMerchant().getId(), toolsList.getAgentProfileHash()), toolsList);
            savedCount++;
        }

        void clear() {
            records.clear();
        }

        int savedCount() {
            return savedCount;
        }

        private record Key(
                UUID merchantId,
                String agentProfileHash
        ) {
        }
    }

    private static class FakeMcpToolClient extends MerchantMcpToolClient {

        private final Queue<MerchantMcpToolsListFetchResult> responses = new ArrayDeque<>();
        private int callCount;

        FakeMcpToolClient() {
            super(RestClient.builder().build());
        }

        @Override
        public MerchantMcpToolsListFetchResult listTools(Merchant merchant) {
            callCount++;
            return responses.remove();
        }

        void enqueue(MerchantMcpToolsListFetchResult response) {
            responses.add(response);
        }

        int callCount() {
            return callCount;
        }
    }

    private static class MutableProfileHashProvider implements AgentProfileHashProvider {

        private String hash;

        MutableProfileHashProvider(String hash) {
            this.hash = hash;
        }

        @Override
        public String currentHash() {
            return hash;
        }

        void setHash(String hash) {
            this.hash = hash;
        }
    }

    private static class MutableTicker implements Ticker {

        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
