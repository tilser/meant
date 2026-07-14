package com.meant.api.module.catalog.service.dto;

import java.util.List;

/**
 * Full transient product detail returned by a current provider get-product call.
 *
 * <p>This projection is deliberately separate from durable product references and is never persisted.
 */
public record RehydratedProductDetails(
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
        List<SelectedOption> selected,
        List<Variant> variants,
        Integer totalVariants,
        PriceRange priceRange,
        PriceRange listPriceRange,
        Boolean requiresSellingPlan,
        Variant selectedVariant,
        List<String> skus,
        List<String> certifications,
        List<String> materials,
        List<String> collections,
        List<Attribute> attributes,
        List<Message> messages,
        Double ratingScore,
        Double ratingScaleMax,
        Long reviewCount,
        String merchantName
) {
    public RehydratedProductDetails(
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
            Boolean requiresSellingPlan,
            Variant selectedVariant,
            List<String> skus,
            List<String> certifications,
            List<String> materials,
            List<String> collections,
            List<Attribute> attributes,
            List<Message> messages,
            Double ratingScore,
            Double ratingScaleMax,
            Long reviewCount,
            String merchantName
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
                selectedVariant == null ? List.of() : selectedVariant.selectedOptions(),
                variants,
                totalVariants,
                priceRange,
                listPriceRange,
                requiresSellingPlan,
                selectedVariant,
                skus,
                certifications,
                materials,
                collections,
                attributes,
                messages,
                ratingScore,
                ratingScaleMax,
                reviewCount,
                merchantName
        );
    }

    public RehydratedProductDetails {
        images = immutable(images);
        media = immutable(media);
        categories = immutable(categories);
        tags = immutable(tags);
        options = immutable(options);
        selected = immutable(selected);
        variants = immutable(variants);
        skus = immutable(skus);
        certifications = immutable(certifications);
        materials = immutable(materials);
        collections = immutable(collections);
        attributes = immutable(attributes);
        messages = immutable(messages);
    }

    public record Image(String url, String altText) {
    }

    public record Media(String type, String url, String altText, String previewImageUrl) {
    }

    public record Category(String value, String taxonomy) {
    }

    public record Option(String name, List<String> values, List<OptionValue> valueDetails) {
        public Option {
            values = immutable(values);
            valueDetails = immutable(valueDetails);
        }

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

    public record OptionValue(String value, Boolean available, Boolean exists) {
    }

    public record PriceRange(String min, String max, String currency) {
    }

    public record SelectedOption(String name, String value) {
    }

    public record Variant(
            String variantId,
            String handle,
            String title,
            String description,
            String url,
            String priceAmount,
            String priceCurrency,
            String listPriceAmount,
            String listPriceCurrency,
            String sku,
            String imageUrl,
            String imageAltText,
            List<Media> media,
            Boolean available,
            List<SelectedOption> selectedOptions,
            List<Category> categories,
            List<String> tags,
            List<Attribute> attributes
    ) {
        public Variant {
            media = immutable(media);
            selectedOptions = immutable(selectedOptions);
            categories = immutable(categories);
            tags = immutable(tags);
            attributes = immutable(attributes);
        }
    }

    public record Attribute(String name, String value) {
    }

    public record Message(
            String type,
            String code,
            String path,
            String contentType,
            String content,
            String severity,
            String presentation,
            String imageUrl,
            String url
    ) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
