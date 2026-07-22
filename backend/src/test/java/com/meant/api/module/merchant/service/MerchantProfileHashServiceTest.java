package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.UcpPaymentHandlerDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MerchantProfileHashServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MerchantProfileHashService service = new MerchantProfileHashService();

    @Test
    void paymentHandlerIdentityConfigContributesToTheProfileHash() throws Exception {
        String first = service.hash(profile("17756429"), profileData());
        String second = service.hash(profile("75578507518"), profileData());

        assertThat(first).isNotEqualTo(second);
    }

    private UcpProfile profile(String shopId) throws Exception {
        return new UcpProfile(
                "2026-04-08",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("dev.shopify.shop_pay", List.of(new UcpPaymentHandlerDefinition(
                        "shop_pay",
                        "2026-04-08",
                        null,
                        null,
                        objectMapper.readTree("{\"shop_id\":\"%s\"}".formatted(shopId))
                )))
        );
    }

    private MerchantProfileData profileData() {
        return new MerchantProfileData(
                "YoungLA",
                "Fitness apparel",
                "About YoungLA",
                "Athletes",
                "Tell me about your store?",
                "Store profile",
                List.of("Fitness"),
                List.of("Gym clothes")
        );
    }
}
