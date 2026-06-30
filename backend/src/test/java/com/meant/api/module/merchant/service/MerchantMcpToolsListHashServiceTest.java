package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MerchantMcpToolsListHashServiceTest {

    private final MerchantMcpToolsListHashService service = new MerchantMcpToolsListHashService();

    @Test
    void hashRejectsNullToolsListRawWithoutNullPointerException() {
        assertThatThrownBy(() -> service.hash(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolsListRaw cannot be null");
    }
}
