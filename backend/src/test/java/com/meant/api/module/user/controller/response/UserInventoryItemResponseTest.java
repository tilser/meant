package com.meant.api.module.user.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserInventoryItemResponseTest {

    @Test
    void sanitizesBuyerTextAndRejectsProtocolProductLinks() {
        Instant now = Instant.parse("2026-07-23T18:30:00Z");
        UserInventoryItemResult result = new UserInventoryItemResult(
                UUID.randomUUID(),
                UserInventorySource.MEANT_PURCHASE,
                "product-1",
                "hash",
                "Shoe from seller.myshopify.com",
                "seller.myshopify.com",
                UserInventoryCategory.APPAREL,
                "See /.well-known/ucp.json",
                "https://mcp.gateway.example/products/image.png",
                "https://mcp.gateway.example/products/shoe",
                null,
                null,
                1,
                "pair",
                "Closet",
                "Retry /mcp",
                "10",
                "Blue",
                "Leather",
                List.of("Source seller.myshopify.com"),
                false,
                false,
                null,
                now,
                null,
                new UserInventoryCommerceReference(
                        "SHOPIFY",
                        null,
                        "gid://shopify/Shop/1",
                        "seller.myshopify.com",
                        "nycfactory.com",
                        "canonical-1",
                        "offer-1",
                        "PROVIDER_CATALOG",
                        "catalog",
                        "product-1",
                        "variant-1",
                        List.of()
                ),
                null,
                now,
                now
        );

        UserInventoryItemResponse response = UserInventoryItemResponse.from(result);

        assertThat(response.name()).isEqualTo("Shoe from nycfactory.com");
        assertThat(response.brand()).isEqualTo("nycfactory.com");
        assertThat(response.description()).isEqualTo("See nycfactory.com");
        assertThat(response.notes()).isEqualTo("Retry nycfactory.com");
        assertThat(response.attributes()).containsExactly("Source nycfactory.com");
        assertThat(response.imageUrl()).isNull();
        assertThat(response.productUrl()).isNull();
        assertThat(response.commerceReference().merchantOrigin()).isEqualTo("nycfactory.com");
    }
}
