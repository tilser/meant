package com.meant.api.plugin.payment.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentCredential(
        String type,
        String token,
        @JsonProperty("details")
        PaymentCredentialDetails details
) {

    public PaymentCredential {
        type = PaymentBindingValidator.requireText(type, "type");
        token = PaymentBindingValidator.requireText(token, "token");
        Objects.requireNonNull(details, "details must not be null");
    }
}
