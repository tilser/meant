package com.meant.api.provider.shopify.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReceiveShopifyOrderWebhookCommand(
        @NotBlank
        String shopDomain,
        @NotBlank
        String topic,
        String webhookId,
        @NotBlank
        String hmac,
        @NotNull
        byte[] body
) {
}
