package com.meant.api.module.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscountCodeCandidateNormalizationServiceTest {

    private final DiscountCodeCandidateNormalizationService service = new DiscountCodeCandidateNormalizationService();

    @Test
    void trimsDeduplicatesCaseInsensitivelyAndHonorsLimit() {
        List<DiscountCodeCandidateSource> normalized = service.normalize(List.of(
                candidate(" save10 "),
                candidate("SAVE10"),
                candidate("Deal20"),
                candidate("DEAL20"),
                candidate("THIRD")
        ), 2);

        assertThat(normalized)
                .extracting(DiscountCodeCandidateSource::code)
                .containsExactly("SAVE10", "DEAL20");
    }

    private DiscountCodeCandidateSource candidate(String code) {
        return new DiscountCodeCandidateSource(
                code,
                "Title",
                "Description",
                "https://merchant.example/codes",
                0.8,
                "",
                "",
                ""
        );
    }
}
