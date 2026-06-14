package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.StorePolicyFaqEntry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantDomainMcpClientTest {

    @Test
    void parsesMcpContentTextIntoStorePolicyFaqEntry() {
        MerchantDomainMcpClient client = new MerchantDomainMcpClient(RestClient.builder(), new ObjectMapper());

        StorePolicyFaqEntry entry = client.parseStorePolicyFaqContent("""
                [{"question":"Tell me about your store?","answer":"Description: Clothes\\nAbout us: We sell clothes\\nTarget audience: Adults\\nCategories: Apparel, Shoes\\nPopular searches: jeans, dresses"}]
                """);

        assertThat(entry.question()).isEqualTo("Tell me about your store?");
        assertThat(entry.answer()).contains("Description: Clothes");
    }
}
