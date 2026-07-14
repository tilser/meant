package com.meant.api.plugin.catalog.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDetailsResponse(
        Product product,
        String instructions,
        List<Message> messages
) {

    public ProductDetailsResponse(Product product, String instructions) {
        this(product, instructions, List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            @JsonProperty("product_id")
            @JsonAlias({"productId", "id"})
            String productId,
            String handle,
            String title,
            String description,
            String url,
            @JsonProperty("image_url")
            String imageUrl,
            List<Image> images,
            List<Media> media,
            List<Category> categories,
            List<String> tags,
            List<Option> options,
            List<SelectedOption> selected,
            List<Variant> variants,
            @JsonProperty("total_variants")
            Integer totalVariants,
            @JsonProperty("price_range")
            PriceRange priceRange,
            @JsonProperty("list_price_range")
            @JsonAlias({"listPriceRange", "compare_at_price_range", "compareAtPriceRange"})
            PriceRange listPriceRange,
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
                String handle,
                String title,
                String description,
                String url,
                String imageUrl,
                List<Image> images,
                List<Media> media,
                List<Category> categories,
                List<String> tags,
                List<Option> options,
                List<Variant> variants,
                Integer totalVariants,
                PriceRange priceRange,
                PriceRange listPriceRange,
                Object listPrice,
                Object rating,
                Object reviewCount,
                Boolean requiresSellingPlan,
                List<Object> sellingPlanGroups,
                Object skus,
                Object certifications,
                Object materials,
                Object collections,
                Object metadata,
                Object metafields,
                Object techSpecs,
                SelectedVariant selectedOrFirstAvailableVariant
        ) {
            this(
                    productId,
                    handle,
                    title,
                    description,
                    url,
                    imageUrl,
                    images,
                    media,
                    categories,
                    tags,
                    options,
                    selectedOrFirstAvailableVariant == null
                            ? List.of()
                            : selectedOrFirstAvailableVariant.selectedOptions(),
                    variants,
                    totalVariants,
                    priceRange,
                    listPriceRange,
                    listPrice,
                    rating,
                    reviewCount,
                    requiresSellingPlan,
                    sellingPlanGroups,
                    skus,
                    certifications,
                    materials,
                    collections,
                    metadata,
                    metafields,
                    techSpecs,
                    selectedOrFirstAvailableVariant
            );
        }

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
                    null,
                    title,
                    description,
                    url,
                    imageUrl,
                    images,
                    List.of(),
                    List.of(),
                    List.of(),
                    options,
                    selectedOrFirstAvailableVariant == null
                            ? List.of()
                            : selectedOrFirstAvailableVariant.selectedOptions(),
                    List.of(),
                    totalVariants,
                    priceRange,
                    null,
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
            List<String> values,
            @JsonProperty("value_details")
            @JsonAlias("valueDetails")
            List<OptionValue> valueDetails
    ) {
        public Option(String name, List<String> values) {
            this(
                    name,
                    values,
                    values == null
                            ? List.of()
                            : values.stream().map(value -> new OptionValue(value, null, null)).toList()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionValue(
            String value,
            Boolean available,
            Boolean exists
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String value,
            String taxonomy
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
            @JsonAlias("label")
            String value
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(
            @JsonProperty("variant_id")
            @JsonAlias({"variantId", "id"})
            String variantId,
            String handle,
            String title,
            String description,
            String url,
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
            List<SelectedOption> selectedOptions,
            List<Category> categories,
            List<String> tags,
            Object metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String type,
            String code,
            String path,
            @JsonProperty("content_type")
            @JsonAlias("contentType")
            String contentType,
            String content,
            String severity,
            String presentation,
            @JsonProperty("image_url")
            @JsonAlias("imageUrl")
            String imageUrl,
            String url
    ) {
    }
}
