package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CartAddItem(
        @JsonProperty("product_id")
        String productId,
        @JsonProperty("product_variant_id")
        String productVariantId,
        @JsonProperty("selected_options")
        List<SelectedOption> selectedOptions,
        List<Component> components,
        @JsonProperty("selling_plan")
        SellingPlan sellingPlan,
        Integer quantity
) {
    public CartAddItem {
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        components = components == null ? List.of() : List.copyOf(components);
    }

    public CartAddItem(String productVariantId, Integer quantity) {
        this(null, productVariantId, List.of(), List.of(), null, quantity);
    }

    public record SelectedOption(String group, String name, String value) {
    }

    public record Component(
            @JsonProperty("product_id") String productId,
            @JsonProperty("product_variant_id") String productVariantId,
            Integer quantity,
            @JsonProperty("selected_options") List<SelectedOption> selectedOptions
    ) {
        public Component {
            selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        }
    }

    public record SellingPlan(
            @JsonProperty("group_id") String groupId,
            @JsonProperty("plan_id") String planId,
            List<Option> options
    ) {
        public SellingPlan {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Option(String name, String value) {
    }
}
