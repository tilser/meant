package com.meant.api.module.discount.service.dto;

public record DiscountCodeCandidateSource(
        String code,
        String title,
        String description,
        String sourceUrl,
        Double confidence,
        String restrictions,
        String validFromText,
        String validUntilText
) {

    public DiscountCodeCandidateSource withCode(String normalizedCode) {
        return new DiscountCodeCandidateSource(
                normalizedCode,
                title,
                description,
                sourceUrl,
                confidence,
                restrictions,
                validFromText,
                validUntilText
        );
    }
}
