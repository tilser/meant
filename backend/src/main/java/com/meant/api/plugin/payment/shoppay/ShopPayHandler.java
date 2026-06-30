package com.meant.api.plugin.payment.shoppay;

import com.meant.api.plugin.payment.common.PaymentHandler;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentCredential;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;
import com.meant.api.plugin.payment.common.support.PaymentHandlerJson;
import com.meant.api.plugin.payment.common.support.PaymentResultValues;
import com.meant.api.plugin.payment.shoppay.dto.ShopPayCredentialDetails;
import com.meant.api.plugin.payment.shoppay.dto.ShopPayCredentialRequest;
import com.meant.api.plugin.payment.shoppay.dto.ShopPayPaymentResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

@Component
@Validated
public class ShopPayHandler implements PaymentHandler<ShopPayCredentialRequest, ShopPayPaymentResult> {

    public static final String ID = "shop-pay";
    public static final String HANDLER_NAME = "com.shopify.shop_pay";

    private static final Duration MAX_CRYPTOGRAM_AGE = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;

    public ShopPayHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShopPayHandler() {
        this(new ObjectMapper());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> names() {
        return List.of(HANDLER_NAME, "shop_pay", "shop-pay");
    }

    @Override
    public PaymentInstrument buildCredential(ShopPayCredentialRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PaymentBinding binding = request.binding();
        PaymentBindingValidator.requireFreshCryptogram(
                request.cryptogramIssuedAt(),
                request.cryptogramExpiresAt(),
                request.validatedAt(),
                MAX_CRYPTOGRAM_AGE,
                "Shop Pay"
        );

        ShopPayCredentialDetails details = new ShopPayCredentialDetails(
                binding.pspMerchantId(),
                binding.pspMerchantDomain(),
                request.cryptogram(),
                request.cryptogramIssuedAt(),
                request.cryptogramExpiresAt(),
                binding
        );
        return new PaymentInstrument(
                HANDLER_NAME,
                binding.amountMinor(),
                binding.currency(),
                new PaymentCredential("shop_pay", request.mandateToken(), details),
                PaymentScaLiability.shiftedTo("shop_pay", "fresh Shop Pay cryptogram")
        );
    }

    @Override
    public ShopPayPaymentResult parseResult(Object result, PaymentBinding expectedBinding) {
        Objects.requireNonNull(expectedBinding, "expectedBinding must not be null");
        Map<String, Object> values = PaymentHandlerJson.map(objectMapper, result, "Shop Pay result");
        PaymentBinding actualBinding = new PaymentBinding(
                PaymentResultValues.text(values, "checkout id", "checkout_id", "checkoutId"),
                PaymentResultValues.text(values, "merchant id", "merchant_id", "merchantId"),
                PaymentResultValues.text(values, "merchant domain", "merchant_domain", "merchantDomain"),
                PaymentResultValues.text(values, "Shop Pay merchant id", "shop_pay_merchant_id", "shopPayMerchantId"),
                PaymentResultValues.text(values, "Shop Pay domain", "shop_pay_domain", "shopPayDomain"),
                PaymentResultValues.amountMinor(values, "amount", "amount", "amount_minor", "amountMinor"),
                PaymentResultValues.text(values, "currency", "currency")
        );
        PaymentBindingValidator.requireBinding(expectedBinding, actualBinding, "Shop Pay result");
        return new ShopPayPaymentResult(
                PaymentResultValues.text(values, "status", "status"),
                PaymentResultValues.text(values, "payment id", "payment_id", "paymentId", "id"),
                PaymentResultValues.optionalText(values, "shop_pay_charge_id", "shopPayChargeId", "charge_id"),
                actualBinding,
                PaymentScaLiability.shiftedTo("shop_pay", "Shop Pay result cryptogram verified by PSP")
        );
    }
}
