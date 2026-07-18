package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSellingPlanGroup(
        String id,
        String name,
        @JsonProperty("app_name")
        @JsonAlias("appName")
        String appName,
        List<GroupOption> options,
        @JsonProperty("selling_plans")
        @JsonAlias("sellingPlans")
        List<SellingPlan> sellingPlans
) {

    public ProductSellingPlanGroup {
        options = immutable(options);
        sellingPlans = immutable(sellingPlans);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupOption(
            String name,
            List<String> values
    ) {

        public GroupOption {
            values = immutable(values);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SellingPlan(
            String id,
            String name,
            String description,
            List<Option> options
    ) {

        public SellingPlan {
            options = immutable(options);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            String name,
            String value
    ) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
