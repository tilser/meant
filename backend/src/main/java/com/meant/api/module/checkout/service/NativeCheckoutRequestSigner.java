package com.meant.api.module.checkout.service;

import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.plugin.checkout.complete.CompleteCheckoutCapability;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.signing.Jcs;
import com.meant.api.plugin.signing.Rfc9421Signer;
import com.meant.api.plugin.support.UcpSession;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class NativeCheckoutRequestSigner {

    private static final String UCP_AGENT_HEADER = "UCP-Agent";
    private static final String UCP_AGENT_VALUE = "meant";

    private final CompleteCheckoutCapability completeCheckoutCapability;
    private final Rfc9421Signer rfc9421Signer;
    private final Jcs jcs;
    private final ObjectMapper objectMapper;

    public byte[] canonicalCompleteBody(CompleteCheckoutRequest request, UcpSession session) throws JacksonException {
        Object arguments = completeCheckoutCapability.buildArguments(request, session.activeCapabilities());
        return jcs.canonicalizeToUtf8Bytes(objectMapper.writeValueAsBytes(arguments));
    }

    public byte[] canonicalCancelBody(CancelCheckoutRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", request.checkoutId());
            return jcs.canonicalizeToUtf8Bytes(objectMapper.writeValueAsBytes(body));
        } catch (JacksonException exception) {
            throw new CheckoutSafetyException("Cancel checkout payload could not be canonicalized", exception);
        }
    }

    public Map<String, String> signedHeaders(MerchantCartProvider provider, byte[] body, String idempotencyKey) {
        String requestId = UUID.randomUUID().toString();
        Rfc9421Signer.SignedRequest signedRequest = rfc9421Signer.sign(new Rfc9421Signer.SigningRequest(
                "POST",
                signingEndpoint(provider),
                MediaType.APPLICATION_JSON_VALUE,
                idempotencyKey,
                requestId,
                body
        ));
        Map<String, String> headers = new LinkedHashMap<>(signedRequest.headers());
        headers.put(UCP_AGENT_HEADER, UCP_AGENT_VALUE);
        return headers;
    }

    private URI signingEndpoint(MerchantCartProvider provider) {
        String endpoint = NativeCheckoutValueSupport.firstText(
                provider.profileMcpEndpoint(),
                provider.advertisedMcpEndpoint()
        );
        if (!StringUtils.hasText(endpoint)) {
            endpoint = "https://" + provider.domain() + "/api/mcp";
        }
        return URI.create(endpoint);
    }
}
