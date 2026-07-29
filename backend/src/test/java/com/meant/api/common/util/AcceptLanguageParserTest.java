package com.meant.api.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcceptLanguageParserTest {

    @Test
    void selectsTheHighestQualityConcreteLanguageAndCanonicalizesIt() {
        assertThat(AcceptLanguageParser.preferredLanguage(
                "en-US;q=0.7, fr-ch;q=0.9, de;q=0.8"
        )).isEqualTo("fr-CH");
    }

    @Test
    void preservesHeaderOrderWhenConcreteLanguagesHaveEqualQuality() {
        assertThat(AcceptLanguageParser.preferredLanguage(
                "de-DE;q=0.8, fr-FR;q=0.8"
        )).isEqualTo("de-DE");
    }

    @Test
    void ignoresWildcardsMalformedRangesAndUnacceptableLanguages() {
        assertThat(AcceptLanguageParser.preferredLanguage(
                "*;q=1, en_US;q=0.9, fr-FR;q=0, cs-CZ;q=0.8"
        )).isEqualTo("cs-CZ");
    }

    @Test
    void doesNotCreateALanguageWhenTheRawHeaderIsAbsentOrUnsafe() {
        assertThat(AcceptLanguageParser.preferredLanguage(null)).isNull();
        assertThat(AcceptLanguageParser.preferredLanguage(" ")).isNull();
        assertThat(AcceptLanguageParser.preferredLanguage("en-US\r\nX-Test: value")).isNull();
        assertThat(AcceptLanguageParser.preferredLanguage("*")).isNull();
    }

    @Test
    void skipsAnInvalidHigherPreferenceInsteadOfGuessingFromIt() {
        assertThat(AcceptLanguageParser.preferredLanguage(
                "not_a_tag;q=1, es-MX;q=0.9"
        )).isEqualTo("es-MX");
    }
}
