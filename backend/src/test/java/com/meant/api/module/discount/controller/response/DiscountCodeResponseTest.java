package com.meant.api.module.discount.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiscountCodeResponseTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void replacesTransportCoordinatesInTextAndRejectsMcpSourceUrl() {
        String endpoint = "https://seller.myshopify.com/api/ucp/mcp";

        DiscountCodeResponse response = DiscountCodeResponse.from(
                result(
                        "SAVE10",
                        "Save at " + endpoint,
                        "Validated through seller.myshopify.com",
                        endpoint,
                        "Only at seller.myshopify.com",
                        "Accepted by " + endpoint
                ),
                "merchant.example"
        );

        assertThat(response).isNotNull();
        assertThat(response.sourceUrl()).isNull();
        assertThat(response.title())
                .contains("merchant.example")
                .doesNotContain("myshopify.com", "/api/ucp/mcp");
        assertThat(response.description())
                .contains("merchant.example")
                .doesNotContain("myshopify.com");
        assertThat(response.restrictions()).doesNotContain("myshopify.com");
        assertThat(response.validationMessage()).doesNotContain("myshopify.com", "/api/ucp/mcp");
    }

    @Test
    void preservesOrdinaryHttpsSourceAndRejectsUnsafeCode() {
        DiscountCodeResponse response = DiscountCodeResponse.from(
                result(
                        " SAVE_10 ",
                        "Ten percent off",
                        "Use on eligible products.",
                        "https://merchant.example/discounts/save-10",
                        "Selected products only.",
                        "Accepted"
                ),
                "merchant.example"
        );

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("SAVE_10");
        assertThat(response.sourceUrl())
                .isEqualTo("https://merchant.example/discounts/save-10");

        assertThat(DiscountCodeResponse.from(
                result(
                        "https://seller.myshopify.com/mcp",
                        "Invalid",
                        "Invalid",
                        null,
                        "",
                        ""
                ),
                "merchant.example"
        )).isNull();
    }

    private DiscountCodeResult result(
            String code,
            String title,
            String description,
            String sourceUrl,
            String restrictions,
            String validationMessage
    ) {
        return new DiscountCodeResult(
                code,
                title,
                description,
                sourceUrl,
                0.9,
                restrictions,
                null,
                EXPIRES_AT,
                validationMessage
        );
    }
}
