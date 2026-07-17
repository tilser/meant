package com.meant.api.provider.shopify.checkout;

import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.module.checkout.service.port.CheckoutRequestHeaderContributor;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Adds Shopify's trusted buyer-IP header to provider-based checkout calls. */
@Component
public class ShopifyCheckoutRequestHeaderContributor implements CheckoutRequestHeaderContributor {

    static final String BUYER_IP_HEADER = "Shopify-Buyer-IP";

    @Override
    public boolean supports(MerchantCartProvider provider, CommerceOperation operation) {
        return provider != null
                && provider.executionPolicy().decision(operation).provider() == MerchantIntegrationProvider.SHOPIFY;
    }

    @Override
    public Map<String, String> headers(CheckoutToolCallContext context) {
        return context.buyerIp() == null
                ? Map.of()
                : Map.of(BUYER_IP_HEADER, context.buyerIp());
    }
}
