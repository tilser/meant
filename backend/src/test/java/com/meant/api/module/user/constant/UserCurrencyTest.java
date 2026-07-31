package com.meant.api.module.user.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserCurrencyTest {

    @Test
    void supportsExactlyTheCurrenciesExposedByAccountSettings() {
        assertThat(UserCurrency.supportedCodes()).isEqualTo(Set.of(
                "USD", "EUR", "GBP", "CZK", "CAD", "AUD", "NZD", "JPY", "CHF", "PLN",
                "SEK", "NOK", "DKK", "HUF", "CNY", "HKD", "SGD", "INR", "KRW"
        ));
    }

    @Test
    void rejectsOtherwiseValidIsoCodesOutsideTheSupportedSet() {
        assertThatThrownBy(() -> UserCurrency.normalize("XAU"))
                .hasMessageContaining("supported ISO 4217 currency codes");
    }
}
