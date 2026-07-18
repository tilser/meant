package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.plugin.checkout.complete.CompleteCheckoutCapability;
import com.meant.api.plugin.checkout.complete.dto.CheckoutSignals;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.signing.Jcs;
import com.meant.api.plugin.support.UcpSession;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NativeCheckoutRequestSignerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NativeCheckoutRequestSigner signer = new NativeCheckoutRequestSigner(
            new CompleteCheckoutCapability(objectMapper),
            null,
            new Jcs(),
            objectMapper
    );

    @Test
    void canonicalCompleteBodyUsesTypedCompleteArguments() throws Exception {
        byte[] body = signer.canonicalCompleteBody(
                new CompleteCheckoutRequest(
                        "checkout-1",
                        List.of(),
                        null,
                        new CheckoutSignals("meant", "test-agent")
                ),
                UcpSession.start()
        );

        JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        assertThat(json.path("id").stringValue()).isEqualTo("checkout-1");
        assertThat(json.path("checkout").path("signals").path("user_agent").stringValue())
                .isEqualTo("test-agent");
    }

    @Test
    void canonicalCancelBodyUsesTypedCancelArguments() throws Exception {
        byte[] body = signer.canonicalCancelBody(new CancelCheckoutRequest("checkout-1", null));

        JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        assertThat(json.path("id").stringValue()).isEqualTo("checkout-1");
        assertThat(json.size()).isEqualTo(1);
    }
}
