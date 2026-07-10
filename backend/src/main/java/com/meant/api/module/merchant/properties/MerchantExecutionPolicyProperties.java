package com.meant.api.module.merchant.properties;

import com.meant.api.module.merchant.constant.CommerceOperation;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.execution-policy")
public record MerchantExecutionPolicyProperties(
        @NotNull Boolean catalogRolloutEnabled,
        @NotNull Boolean cartRolloutEnabled,
        @NotNull Boolean checkoutSessionRolloutEnabled,
        @NotNull Boolean embeddedCheckoutRolloutEnabled,
        @NotNull Boolean directCheckoutCompletionRolloutEnabled,
        @NotNull Boolean orderReadsRolloutEnabled,
        @NotNull Boolean orderWebhooksRolloutEnabled
) {

    public boolean rolloutEnabled(CommerceOperation operation, boolean legacyNativeCheckoutRollout) {
        return switch (operation) {
            case CATALOG -> catalogRolloutEnabled;
            case CART -> cartRolloutEnabled;
            case CHECKOUT_SESSION -> checkoutSessionRolloutEnabled;
            case EMBEDDED_CHECKOUT -> embeddedCheckoutRolloutEnabled;
            case DIRECT_CHECKOUT_COMPLETION -> directCheckoutCompletionRolloutEnabled
                    || legacyNativeCheckoutRollout;
            case ORDER_READS -> orderReadsRolloutEnabled;
            case ORDER_WEBHOOKS -> orderWebhooksRolloutEnabled;
        };
    }
}
