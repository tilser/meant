package com.meant.api.module.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscountMerchantLookupServiceTest {

    @Test
    void domainLookupFallsBackFromWwwToRootDomain() {
        Merchant merchant = merchant("merchant.example");
        List<String> calls = new ArrayList<>();
        MerchantRepository merchantRepository = merchantRepository(Map.of("merchant.example", merchant), calls);
        DiscountMerchantLookupService service = new DiscountMerchantLookupService(merchantRepository);

        var result = service.find(null, "https://www.merchant.example/path");

        assertThat(result.id()).isEqualTo(merchant.getId());
        assertThat(result.domain()).isEqualTo("merchant.example");
        assertThat(calls).containsExactly("www.merchant.example", "merchant.example");
    }

    @Test
    void exactWwwDomainWinsBeforeFallback() {
        Merchant merchant = merchant("www.merchant.example");
        List<String> calls = new ArrayList<>();
        MerchantRepository merchantRepository = merchantRepository(Map.of("www.merchant.example", merchant), calls);
        DiscountMerchantLookupService service = new DiscountMerchantLookupService(merchantRepository);

        var result = service.find(null, "www.merchant.example");

        assertThat(result.domain()).isEqualTo("www.merchant.example");
        assertThat(calls).containsExactly("www.merchant.example");
    }

    @Test
    void rootDomainLookupFallsBackToWwwDomain() {
        Merchant merchant = merchant("www.merchant.example");
        List<String> calls = new ArrayList<>();
        MerchantRepository merchantRepository = merchantRepository(Map.of("www.merchant.example", merchant), calls);
        DiscountMerchantLookupService service = new DiscountMerchantLookupService(merchantRepository);

        var result = service.find(null, "merchant.example");

        assertThat(result.domain()).isEqualTo("www.merchant.example");
        assertThat(calls).containsExactly("merchant.example", "www.merchant.example");
    }

    private Merchant merchant(String domain) {
        Instant now = Instant.parse("2026-07-03T10:00:00Z");
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain(domain)
                .name("Merchant")
                .ucpUrl("https://" + domain)
                .ucpVersion("2026-04-08")
                .advertisedMcpEndpoint("https://" + domain + "/mcp")
                .profileHash("hash")
                .description("")
                .about("")
                .targetAudience("")
                .profileQuestion("")
                .profileAnswerRaw("")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private MerchantRepository merchantRepository(Map<String, Merchant> merchants, List<String> calls) {
        return (MerchantRepository) Proxy.newProxyInstance(
                MerchantRepository.class.getClassLoader(),
                new Class<?>[]{MerchantRepository.class},
                (proxy, method, args) -> {
                    if ("findByDomain".equals(method.getName())) {
                        String domain = (String) args[0];
                        calls.add(domain);
                        return Optional.ofNullable(merchants.get(domain));
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeMerchantRepository";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
