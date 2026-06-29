package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
            @JsonAlias({"productId", "id"})
            String productId,
            String title,
            String description,
            String url,
            @JsonProperty("image_url")
            String imageUrl,
            List<Image> images,
            List<Media> media,
            List<Option> options,
            @JsonProperty("total_variants")
            Integer totalVariants,
            @JsonProperty("price_range")
            PriceRange priceRange,
            @JsonProperty("list_price")
            @JsonAlias({"compare_at_price", "compareAtPrice", "original_price", "regular_price", "was_price"})
            Object listPrice,
            @JsonProperty("rating")
            @JsonAlias({"aggregate_rating", "aggregateRating", "ratings"})
            Object rating,
            @JsonProperty("review_count")
            @JsonAlias({"reviews_count", "reviewCount", "reviewsCount", "rating_count", "ratingCount"})
            Object reviewCount,
            @JsonProperty("requires_selling_plan")
            Boolean requiresSellingPlan,
            @JsonProperty("selling_plan_groups")
            List<Object> sellingPlanGroups,
            Object skus,
            Object certifications,
            Object materials,
            Object collections,
            Object metadata,
            Object metafields,
            @JsonProperty("tech_specs")
            @JsonAlias({"techSpecs", "specifications"})
            Object techSpecs,
            @JsonProperty("selectedOrFirstAvailableVariant")
            @JsonAlias({"selected_or_first_available_variant", "selected_variant", "selectedVariant"})
            SelectedVariant selectedOrFirstAvailableVariant
    ) {
        public Product(
                String productId,
                String title,
                String description,
                String url,
                String imageUrl,
                List<Image> images,
                List<Option> options,
                Integer totalVariants,
                PriceRange priceRange,
                Boolean requiresSellingPlan,
                List<Object> sellingPlanGroups,
                SelectedVariant selectedOrFirstAvailableVariant
        ) {
            this(
                    productId,
                    title,
                    description,
                    url,
                    imageUrl,
                    images,
                    List.of(),
                    options,
                    totalVariants,
                    priceRange,
                    null,
                    null,
                    null,
                    requiresSellingPlan,
                    sellingPlanGroups,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    selectedOrFirstAvailableVariant
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(
            String url,
            @JsonProperty("alt_text")
            String altText
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
            String type,
            String url,
            @JsonProperty("alt_text")
            @JsonAlias("altText")
            String altText,
            @JsonProperty("preview_image_url")
            @JsonAlias("previewImageUrl")
            String previewImageUrl
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
            @JsonAlias({"variantId", "id"})
            String variantId,
            String title,
            String price,
            String currency,
            String sku,
            @JsonProperty("list_price")
            @JsonAlias({"compare_at_price", "compareAtPrice", "original_price", "regular_price", "was_price"})
            Object listPrice,
            @JsonProperty("image_url")
            String imageUrl,
            @JsonProperty("image_alt_text")
            String imageAltText,
            List<Media> media,
            Boolean available,
            @JsonProperty("selected_options")
            List<SelectedOption> selectedOptions
    ) {
        public SelectedVariant(
                String variantId,
                String title,
                String price,
                String currency,
                String imageUrl,
                String imageAltText,
                Boolean available,
                List<SelectedOption> selectedOptions
        ) {
            this(
                    variantId,
                    title,
                    price,
                    currency,
                    null,
                    null,
                    imageUrl,
                    imageAltText,
                    List.of(),
                    available,
                    selectedOptions
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedOption(
            String name,
            String value
    ) {
    }
}
