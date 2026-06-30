package com.meant.api.plugin.payment.googlepay;

import com.meant.api.plugin.payment.common.PaymentHandler;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentCredential;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;
import com.meant.api.plugin.payment.common.support.PaymentHandlerJson;
import com.meant.api.plugin.payment.common.support.PaymentResultValues;
import com.meant.api.plugin.payment.googlepay.dto.GooglePayCredentialDetails;
import com.meant.api.plugin.payment.googlepay.dto.GooglePayCredentialRequest;
import com.meant.api.plugin.payment.googlepay.dto.GooglePayPaymentResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

@Component
@Validated
public class GooglePayHandler implements PaymentHandler<GooglePayCredentialRequest, GooglePayPaymentResult> {

    public static final String ID = "google-pay";
    public static final String HANDLER_NAME = "com.google.pay";

    private static final Duration MAX_CRYPTOGRAM_AGE = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;

    public GooglePayHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GooglePayHandler() {
        this(new ObjectMapper());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> names() {
        return List.of(HANDLER_NAME, "google_pay", "google-pay");
    }

    @Override
    public PaymentInstrument buildCredential(GooglePayCredentialRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PaymentBinding binding = request.binding();
        PaymentBindingValidator.requireFreshCryptogram(
                request.cryptogramIssuedAt(),
                request.cryptogramExpiresAt(),
                request.validatedAt(),
                MAX_CRYPTOGRAM_AGE,
                "Google Pay"
        );

        GooglePayCredentialDetails details = new GooglePayCredentialDetails(
                request.googlePayMerchantId(),
                binding.pspMerchantId(),
                binding.pspMerchantDomain(),
                request.protocolVersion(),
                request.signature(),
                request.signedMessage(),
                request.cryptogram(),
                request.cryptogramIssuedAt(),
                request.cryptogramExpiresAt(),
                binding
        );
        return new PaymentInstrument(
                HANDLER_NAME,
                binding.amountMinor(),
                binding.currency(),
                new PaymentCredential("google_pay", request.mandateToken(), details),
                PaymentScaLiability.shiftedTo("issuer", "fresh Google Pay network token cryptogram")
        );
    }

    @Override
    public GooglePayPaymentResult parseResult(Object result, PaymentBinding expectedBinding) {
        Objects.requireNonNull(expectedBinding, "expectedBinding must not be null");
        Map<String, Object> values = PaymentHandlerJson.map(objectMapper, result, "Google Pay result");
        PaymentBinding actualBinding = new PaymentBinding(
                PaymentResultValues.text(values, "checkout id", "checkout_id", "checkoutId"),
                PaymentResultValues.text(values, "merchant id", "merchant_id", "merchantId"),
                PaymentResultValues.text(values, "merchant domain", "merchant_domain", "merchantDomain"),
                PaymentResultValues.text(values, "gateway merchant id", "gateway_merchant_id", "gatewayMerchantId"),
                PaymentResultValues.text(values, "Google Pay domain", "google_pay_domain", "googlePayDomain"),
                PaymentResultValues.amountMinor(values, "amount", "amount", "amount_minor", "amountMinor"),
                PaymentResultValues.text(values, "currency", "currency")
        );
        PaymentBindingValidator.requireBinding(expectedBinding, actualBinding, "Google Pay result");
        return new GooglePayPaymentResult(
                PaymentResultValues.text(values, "status", "status"),
                PaymentResultValues.text(values, "payment id", "payment_id", "paymentId", "id"),
                PaymentResultValues.text(values, "Google Pay merchant id", "google_pay_merchant_id", "googlePayMerchantId"),
                actualBinding,
                PaymentScaLiability.shiftedTo("issuer", "Google Pay cryptogram accepted by PSP")
        );
    }
}
