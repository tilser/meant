package com.meant.api.plugin.payment.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenPaymentCredentialDetails(
        String source
) implements PaymentCredentialDetails {
}
