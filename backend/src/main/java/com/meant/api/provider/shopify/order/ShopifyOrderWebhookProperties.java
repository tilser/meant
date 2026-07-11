package com.meant.api.provider.shopify.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "order.webhook")
public record ShopifyOrderWebhookProperties(
        String shopifySecret
) {
}
