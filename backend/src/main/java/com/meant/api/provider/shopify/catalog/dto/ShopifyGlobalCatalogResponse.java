package com.meant.api.provider.shopify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Typed boundary for the Shopify Global Catalog UCP 2026-04-08 response.
 *
 * <p>The optional product_id, selling_plan, and components fields are isolated here so additive
 * provider data can preserve commercial identity if Shopify supplies it. The published Global
 * Catalog extension currently guarantees only the corresponding requires flags.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopifyGlobalCatalogResponse(
        Ucp ucp,
        List<Product> products,
        Product product,
        List<Message> messages,
        Pagination pagination
) {

    public List<Product> resolvedProducts() {
        if (product != null) {
            return List.of(product);
        }
        return products == null ? List.of() : products;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ucp(
            String version,
            String status,
            Map<String, List<CapabilityVersion>> capabilities
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapabilityVersion(String version) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            String id,
            String handle,
            String title,
            Description description,
            String url,
            List<Category> categories,
            @JsonProperty("price_range") PriceRange priceRange,
            @JsonProperty("list_price_range") PriceRange listPriceRange,
            List<Media> media,
            List<Option> options,
            List<SelectedOption> selected,
            List<Variant> variants,
            Rating rating,
            List<String> tags,
            Metadata metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(String plain, String html) {

        public String preferredText() {
            return plain == null || plain.isBlank() ? html : plain;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(String value, String taxonomy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRange(Price min, Price max) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(Long amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
            String type,
            String url,
            @JsonProperty("alt_text") @JsonAlias("altText") String altText,
            Integer width,
            Integer height
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String name, List<OptionValue> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionValue(String label, Boolean available, Boolean exists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedOption(String name, String label) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(
            String id,
            @JsonProperty("product_id") @JsonAlias("productId") String productId,
            String sku,
            String handle,
            String title,
            Description description,
            String url,
            Price price,
            @JsonProperty("list_price") @JsonAlias("listPrice") Price listPrice,
            Availability availability,
            Requires requires,
            List<SelectedOption> options,
            List<Media> media,
            List<Category> categories,
            List<String> tags,
            List<Barcode> barcodes,
            List<Input> inputs,
            Seller seller,
            @JsonProperty("checkout_url") @JsonAlias("checkoutUrl") String checkoutUrl,
            @JsonProperty("selling_plan") @JsonAlias("sellingPlan") SellingPlan sellingPlan,
            List<Component> components
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Availability(
            Boolean available,
            String status,
            Integer quantity,
            @JsonProperty("running_low") Boolean runningLow
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requires(
            Boolean shipping,
            @JsonProperty("selling_plan") Boolean sellingPlan,
            Boolean components
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Barcode(String type, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Input(String id, String match) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Seller(String name, String id, String domain, String url, List<Link> links) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(String type, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SellingPlan(
            String id,
            @JsonProperty("group_id") @JsonAlias("groupId") String groupId,
            List<SellingPlanOption> options
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SellingPlanOption(String name, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Component(
            @JsonProperty("product_id") @JsonAlias("productId") String productId,
            @JsonProperty("variant_id") @JsonAlias("variantId") String variantId,
            Integer quantity,
            List<SelectedOption> options
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rating(
            BigDecimal value,
            @JsonProperty("scale_max") BigDecimal scaleMax,
            Long count
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("tech_specs") List<String> techSpecs,
            @JsonProperty("top_features") List<String> topFeatures,
            @JsonProperty("unique_selling_points") List<String> uniqueSellingPoints
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String type,
            String code,
            String path,
            @JsonProperty("content_type") String contentType,
            String content,
            String severity,
            String presentation,
            @JsonProperty("image_url") String imageUrl,
            String url
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(
            String cursor,
            @JsonProperty("has_next_page") Boolean hasNextPage,
            @JsonProperty("total_count") Long totalCount
    ) {
    }
}
