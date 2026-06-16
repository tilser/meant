package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDetailsResponse(
        Product product,
        String instructions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            @JsonProperty("product_id")
            String productId,
            String title,
            String description,
            String url,
            @JsonProperty("image_url")
            String imageUrl,
            List<Image> images,
            List<Option> options,
            @JsonProperty("total_variants")
            Integer totalVariants,
            @JsonProperty("price_range")
            PriceRange priceRange,
            @JsonProperty("requires_selling_plan")
            Boolean requiresSellingPlan,
            @JsonProperty("selling_plan_groups")
            List<Object> sellingPlanGroups,
            @JsonProperty("selectedOrFirstAvailableVariant")
            SelectedVariant selectedOrFirstAvailableVariant
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(
            String url,
            @JsonProperty("alt_text")
            String altText
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            String name,
            List<String> values
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRange(
            String min,
            String max,
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedVariant(
            @JsonProperty("variant_id")
            String variantId,
            String title,
            String price,
            String currency,
            @JsonProperty("image_url")
            String imageUrl,
            @JsonProperty("image_alt_text")
            String imageAltText,
            Boolean available,
            @JsonProperty("selected_options")
            List<SelectedOption> selectedOptions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedOption(
            String name,
            String value
    ) {
    }
}
