package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.StorePolicyFaqEntry;
import org.junit.jupiter.api.Test;

class MerchantProfileParserTest {

    private final MerchantProfileParser parser = new MerchantProfileParser();

    @Test
    void extractsStoreProfileSectionsAndLists() {
        MerchantProfileData profileData = parser.parse(
                "forever21.com",
                new StorePolicyFaqEntry(
                        "Tell me about your store?",
                        """
                                Description: Trend-focused clothing store.
                                About us: We sell apparel and accessories.
                                Target audience: Young adults.
                                Categories: Women's Clothing, Shoes, Accessories
                                Popular searches: dresses, denim, jewelry
                                """
                )
        );

        assertThat(profileData.name()).isEqualTo("forever21.com");
        assertThat(profileData.description()).isEqualTo("Trend-focused clothing store.");
        assertThat(profileData.about()).isEqualTo("We sell apparel and accessories.");
        assertThat(profileData.targetAudience()).isEqualTo("Young adults.");
        assertThat(profileData.categories()).containsExactly("Women's Clothing", "Shoes", "Accessories");
        assertThat(profileData.popularSearches()).containsExactly("dresses", "denim", "jewelry");
    }

    @Test
    void missingStoreProfileSectionsBecomeEmptyValues() {
        MerchantProfileData profileData = parser.parse(
                "example.com",
                new StorePolicyFaqEntry(
                        "Tell me about your store?",
                        """
                                About us: We sell useful things.
                                Categories: Home, Gifts
                                """
                )
        );

        assertThat(profileData.description()).isEmpty();
        assertThat(profileData.about()).isEqualTo("We sell useful things.");
        assertThat(profileData.targetAudience()).isEmpty();
        assertThat(profileData.categories()).containsExactly("Home", "Gifts");
        assertThat(profileData.popularSearches()).isEmpty();
    }

    @Test
    void stripsNulBytesFromPersistedProfileText() {
        MerchantProfileData profileData = parser.parse(
                "example.com",
                new StorePolicyFaqEntry(
                        "Tell me\u0000 about your store?",
                        """
                                Description: Useful\u0000 products.
                                About us: We sell useful things.
                                Target audience: Everyone.
                                Categories: Home
                                Popular searches: gifts
                                """
                )
        );

        assertThat(profileData.profileQuestion()).isEqualTo("Tell me about your store?");
        assertThat(profileData.description()).isEqualTo("Useful products.");
        assertThat(profileData.profileAnswerRaw()).doesNotContain("\u0000");
    }
}
