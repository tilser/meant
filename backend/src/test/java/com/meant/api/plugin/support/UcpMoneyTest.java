package com.meant.api.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UcpMoneyTest {

    @Test
    void parsesZeroExponentCurrencies() {
        assertThat(UcpMoney.minorAmount("1234", "JPY")).isEqualTo(1234L);
        assertThat(UcpMoney.minorAmount("1234.56", "KRW")).isEqualTo(1235L);
    }

    @Test
    void parsesThreeDecimalCurrencies() {
        assertThat(UcpMoney.minorAmount("1.234", "KWD")).isEqualTo(1234L);
        assertThat(UcpMoney.minorAmount("1,234", "BHD")).isEqualTo(1234L);
    }

    @Test
    void formatsMinorAmountsAsDecimalText() {
        assertThat(UcpDecimal.minorAmountToDecimalText(6000L, "USD")).isEqualTo("60.00");
        assertThat(UcpDecimal.minorAmountToDecimalText(1234L, "JPY")).isEqualTo("1234");
        assertThat(UcpDecimal.minorAmountToDecimalText(1234L, "KWD")).isEqualTo("1.234");
    }

    @Test
    void distinguishesEuropeanAndUsGroupedAmounts() {
        assertThat(UcpMoney.minorAmount("1.234,56", "EUR")).isEqualTo(123456L);
        assertThat(UcpMoney.minorAmount("1,234.56", "USD")).isEqualTo(123456L);
        assertThat(UcpMoney.minorAmount("1,234", "USD")).isEqualTo(123400L);
    }

    @Test
    void preservesMinorUnitHints() {
        assertThat(UcpMoney.value(Map.of("amount_cents", "5,200", "currency", "USD"), null))
                .isEqualTo(new UcpMoney(5200L, "USD"));
        assertThat(UcpMoney.value(Map.of("amount", "5200", "unit", "minor", "currency", "USD"), null))
                .isEqualTo(new UcpMoney(5200L, "USD"));
    }

    @Test
    void handlesRawRecordMoneyAsMinorUnits() {
        assertThat(UcpMoney.value(new CatalogSearchResponse.Money(5200L, "USD"), null))
                .isEqualTo(new UcpMoney(5200L, "USD"));
    }

    @Test
    void handlesNegativeAndBlankAmounts() {
        assertThat(UcpMoney.minorAmount("-12.34", "USD")).isEqualTo(-1234L);
        assertThat(UcpMoney.minorAmount(" ", "USD")).isNull();
        assertThat(UcpMoney.value(Map.of("amount", " ", "currency", "USD"), null)).isNull();
    }

    @Test
    void normalizesRatingsAndReviewCounts() {
        assertThat(UcpDecimal.ratingValue(8)).isEqualTo(4.0d);
        assertThat(UcpDecimal.ratingValue(80)).isEqualTo(4.0d);
        assertThat(UcpDecimal.ratingValue(Map.of("value", "4,75"))).isEqualTo(4.75d);
        assertThat(UcpDecimal.reviewCountValue("1,234,567")).isEqualTo(1234567);
        assertThat(UcpDecimal.reviewCountValue("-5")).isZero();
    }
}
