package com.meant.api.module.order.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "order.webhook")
public record OrderWebhookProperties(
        String shopifySecret
) {
}
