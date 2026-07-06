package com.meant.api.plugin.checkout.common.service;

import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.common.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.extension.ap2mandate.Ap2MandateExtensionSupport;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.signing.Ap2MandateException;
import com.meant.api.plugin.signing.Ap2MandateService;
import com.meant.api.plugin.support.UcpSession;
import com.nimbusds.jose.jwk.ECKey;
import java.text.ParseException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class NativeCheckoutAp2MandateBuilder {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Ap2MandateService ap2MandateService;
    private final ObjectMapper objectMapper;

    public boolean isRequired(NativeCheckoutCompletionCommand command, UcpSession session) {
        return isRequired(command.ap2SecurityLock(), session);
    }

    public boolean isRequired(boolean ap2SecurityLock, UcpSession session) {
        return ap2SecurityLock || Ap2MandateExtensionSupport.active(session.activeCapabilities());
    }

    public String buildCheckoutMandate(
            MerchantCartProvider provider,
            NativeCheckoutCompletionCommand command,
            BuyerConsentArtifact consent,
            UcpCheckoutToolResult refreshedCheckout
    ) throws ParseException, JacksonException {
        NativeCheckoutCompletionCommand.Ap2MandateInput input = command.ap2Mandate();
        if (input == null) {
            throw new Ap2MandateException("AP2 was negotiated but mandate input was not supplied");
        }
        String checkoutPayloadJson = checkoutPayloadJson(refreshedCheckout);
        String merchantAuthorizationJws = StringUtils.hasText(input.merchantAuthorizationJws())
                ? input.merchantAuthorizationJws().trim()
                : merchantAuthorizationJws(checkoutPayloadJson);
        ECKey merchantPublicKey = merchantPublicKey(input);
        return ap2MandateService.buildCheckoutMandate(new Ap2MandateService.BuildMandateCommand(
                checkoutPayloadJson,
                merchantAuthorizationJws,
                merchantPublicKey,
                input.expectedMerchantAuthorizationKid(),
                input.merchantAuthorizationIssuer(),
                provider.merchantId().toString(),
                command.checkoutId(),
                command.userId().toString(),
                input.agentIssuer(),
                input.audience(),
                input.nonce(),
                consent.totalAmountMinor(),
                consent.currency(),
                input.expiresAt()
        )).checkoutMandate();
    }

    private ECKey merchantPublicKey(NativeCheckoutCompletionCommand.Ap2MandateInput input) {
        try {
            return ECKey.parse(objectMapper.writeValueAsString(input.merchantPublicJwk()));
        } catch (JacksonException | ParseException exception) {
            throw new Ap2MandateException("Merchant AP2 public key could not be parsed", exception);
        }
    }

    private String checkoutPayloadJson(UcpCheckoutToolResult result) throws JacksonException {
        Map<String, Object> root = objectMapper.readValue(result.rawResponse(), MAP_TYPE);
        if (root == null) {
            throw new Ap2MandateException("Checkout response payload is empty or invalid");
        }
        Object checkout = root.get("checkout");
        Object payload = checkout instanceof Map<?, ?> ? checkout : root;
        return objectMapper.writeValueAsString(payload);
    }

    private String merchantAuthorizationJws(String checkoutPayloadJson) throws JacksonException {
        Map<String, Object> checkout = objectMapper.readValue(checkoutPayloadJson, MAP_TYPE);
        Object ap2 = checkout.get("ap2");
        if (ap2 instanceof Map<?, ?> ap2Map) {
            String authorization = NativeCheckoutValueSupport.scalarString(NativeCheckoutValueSupport.firstMapValue(
                    ap2Map,
                    "merchant_authorization",
                    "merchantAuthorization"
            ));
            if (StringUtils.hasText(authorization)) {
                return authorization;
            }
        }
        throw new Ap2MandateException("Checkout did not contain AP2 merchant_authorization");
    }
}
