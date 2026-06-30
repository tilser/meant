package com.meant.api.module.order.service;

import com.meant.api.module.order.properties.OrderWebhookProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShopifyOrderWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OrderWebhookProperties properties;

    public boolean verify(byte[] body, String submittedHmac) {
        if (body == null || !hasText(submittedHmac) || !hasText(properties.shopifySecret())) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHmac(body).getBytes(StandardCharsets.UTF_8),
                submittedHmac.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String expectedHmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.shopifySecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify Shopify order webhook signature", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
