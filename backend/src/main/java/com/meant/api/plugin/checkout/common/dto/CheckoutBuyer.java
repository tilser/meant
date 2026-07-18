package com.meant.api.plugin.checkout.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutBuyer(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        @JsonProperty("phone_number") String phoneNumber
) {

    public boolean empty() {
        return isBlank(firstName) && isBlank(lastName) && isBlank(email) && isBlank(phoneNumber);
    }

    public CheckoutBuyer withEmailIfMissing(String fallbackEmail) {
        if (!isBlank(email) || isBlank(fallbackEmail)) {
            return this;
        }
        return new CheckoutBuyer(firstName, lastName, fallbackEmail.trim(), phoneNumber);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
