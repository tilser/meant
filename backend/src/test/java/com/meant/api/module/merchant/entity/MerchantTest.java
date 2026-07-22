package com.meant.api.module.merchant.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MerchantTest {

    @Test
    void existingMerchantKeepsItsPrimarySourceWhenAnAliasRefreshesIt() {
        MerchantRaw primary = MerchantRaw.builder().domain("store.example").build();
        MerchantRaw alias = MerchantRaw.builder().domain("store.myshopify.com").build();
        Merchant merchant = Merchant.builder().merchantRaw(primary).build();
        Instant now = Instant.now();

        merchant.updateProfileMetadata(
                alias,
                "store.example",
                "https://store.myshopify.com/.well-known/ucp",
                "2026-04-08",
                "https://store.myshopify.com/custom/mcp",
                null,
                true,
                now,
                now
        );

        assertThat(merchant.getMerchantRaw()).isSameAs(primary);
    }
}
