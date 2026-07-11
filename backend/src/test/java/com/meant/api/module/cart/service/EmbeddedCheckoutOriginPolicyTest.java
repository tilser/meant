package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.properties.CorsProperties;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddedCheckoutOriginPolicyTest {
    private final EmbeddedCheckoutOriginPolicy policy = new EmbeddedCheckoutOriginPolicy(
            new CorsProperties(List.of("https://meant.com", "http://localhost:3000")));

    @Test
    void normalizesAndAcceptsOnlyConfiguredOrigins() {
        assertThat(policy.requireAllowed("https://MEANT.com:443/")).isEqualTo("https://meant.com");
        assertThat(policy.requireAllowed("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThatThrownBy(() -> policy.requireAllowed("https://evil.example"))
                .isInstanceOf(EmbeddedCheckoutException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://meant.com/path?token=secret"))
                .isInstanceOf(EmbeddedCheckoutException.class);
    }
}
