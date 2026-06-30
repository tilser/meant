package com.meant.api.plugin.payment.card;

import com.meant.api.plugin.payment.card.dto.CardCredentialDetails;
import com.meant.api.plugin.payment.card.dto.CardCredentialRequest;
import com.meant.api.plugin.payment.card.dto.CardPaymentResult;
import com.meant.api.plugin.payment.common.PaymentHandler;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentCredential;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;
import com.meant.api.plugin.payment.common.support.PaymentHandlerJson;
import com.meant.api.plugin.payment.common.support.PaymentResultValues;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

@Component
@Validated
public class CardHandler implements PaymentHandler<CardCredentialRequest, CardPaymentResult> {

    public static final String ID = "card";
    public static final String HANDLER_NAME = "card";

    private static final Duration MAX_CRYPTOGRAM_AGE = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;

    public CardHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> names() {
        return List.of(HANDLER_NAME, "payment_card", "card_token");
    }

    @Override
    public PaymentInstrument buildCredential(CardCredentialRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PaymentBinding binding = request.binding();
        PaymentBindingValidator.requireFreshCryptogram(
                request.cryptogramIssuedAt(),
                request.cryptogramExpiresAt(),
                request.validatedAt(),
                MAX_CRYPTOGRAM_AGE,
                "card"
        );

        PaymentScaLiability scaLiability = scaLiability(request);
        CardCredentialDetails details = new CardCredentialDetails(
                request.cardToken(),
                request.networkTransactionId(),
                request.threeDsServerTransactionId(),
                request.eci(),
                request.cryptogram(),
                request.cryptogramIssuedAt(),
                request.cryptogramExpiresAt(),
                binding,
                scaLiability
        );
        return new PaymentInstrument(
                HANDLER_NAME,
                binding.amountMinor(),
                binding.currency(),
                new PaymentCredential("card", request.mandateToken(), details),
                scaLiability
        );
    }

    @Override
    public CardPaymentResult parseResult(Object result, PaymentBinding expectedBinding) {
        Objects.requireNonNull(expectedBinding, "expectedBinding must not be null");
        Map<String, Object> values = PaymentHandlerJson.map(objectMapper, result, "card payment result");
        PaymentBinding actualBinding = new PaymentBinding(
                PaymentResultValues.text(values, "checkout id", "checkout_id", "checkoutId"),
                PaymentResultValues.text(values, "merchant id", "merchant_id", "merchantId"),
                PaymentResultValues.text(values, "merchant domain", "merchant_domain", "merchantDomain"),
                PaymentResultValues.text(values, "PSP merchant id", "psp_merchant_id", "pspMerchantId"),
                PaymentResultValues.text(values, "PSP merchant domain", "psp_merchant_domain", "pspMerchantDomain"),
                PaymentResultValues.amountMinor(values, "amount", "amount", "amount_minor", "amountMinor"),
                PaymentResultValues.text(values, "currency", "currency")
        );
        PaymentBindingValidator.requireBinding(expectedBinding, actualBinding, "card payment result");
        return new CardPaymentResult(
                PaymentResultValues.text(values, "status", "status"),
                PaymentResultValues.text(values, "payment id", "payment_id", "paymentId", "id"),
                PaymentResultValues.optionalText(values, "network_transaction_id", "networkTransactionId"),
                actualBinding,
                PaymentScaLiability.retainedBy("merchant_psp", "card payment result accepted by PSP")
        );
    }

    private PaymentScaLiability scaLiability(CardCredentialRequest request) {
        if (request.challengeRequired()) {
            return PaymentScaLiability.challengeRequired("issuer", "cardholder challenge required");
        }
        if (request.liabilityShifted()) {
            return PaymentScaLiability.shiftedTo("issuer", "fresh 3DS card cryptogram");
        }
        return PaymentScaLiability.retainedBy("merchant_psp", "card PSP retains SCA liability");
    }
}
