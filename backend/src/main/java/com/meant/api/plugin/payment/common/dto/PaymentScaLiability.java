package com.meant.api.plugin.payment.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentScaLiability(
        @JsonProperty("liable_party")
        String liableParty,
        @JsonProperty("liability_shifted")
        boolean liabilityShifted,
        @JsonProperty("challenge_required")
        boolean challengeRequired,
        String reason
) {

    public PaymentScaLiability {
        liableParty = PaymentBindingValidator.requireText(liableParty, "liableParty");
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public static PaymentScaLiability shiftedTo(String liableParty, String reason) {
        return new PaymentScaLiability(liableParty, true, false, reason);
    }

    public static PaymentScaLiability challengeRequired(String liableParty, String reason) {
        return new PaymentScaLiability(liableParty, false, true, reason);
    }

    public static PaymentScaLiability retainedBy(String liableParty, String reason) {
        return new PaymentScaLiability(liableParty, false, false, reason);
    }
}
