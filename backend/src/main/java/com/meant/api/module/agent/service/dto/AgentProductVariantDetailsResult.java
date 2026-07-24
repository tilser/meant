package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Compact model-facing projection of current selectable product variants. */
public record AgentProductVariantDetailsResult(
        List<OptionGroup> optionGroups,
        List<SelectedOption> selectedOptions,
        String selectedVariantTitle,
        Boolean selectedVariantAvailable,
        int returnedVariantCount,
        Integer totalVariantCount
) {

    public AgentProductVariantDetailsResult {
        optionGroups = optionGroups == null ? List.of() : List.copyOf(optionGroups);
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
    }

    public static AgentProductVariantDetailsResult from(RehydratedProductDetails details) {
        if (details == null) {
            return null;
        }
        List<RehydratedProductDetails.Variant> variants = details.variants().stream()
                .filter(Objects::nonNull)
                .toList();
        List<RehydratedProductDetails.SelectedOption> selected = selectedOptions(details);
        Map<String, OptionGroupBuilder> groups = new LinkedHashMap<>();
        details.options().stream()
                .filter(Objects::nonNull)
                .filter(option -> hasText(option.name()))
                .forEach(option -> {
                    OptionGroupBuilder group = groups.computeIfAbsent(
                            normalized(option.name()),
                            ignored -> new OptionGroupBuilder(option.name().trim())
                    );
                    option.valueDetails().stream()
                            .filter(Objects::nonNull)
                            .filter(value -> hasText(value.value()))
                            .forEach(value -> group.add(
                                    value.value(),
                                    value.exists(),
                                    value.available()
                            ));
                    option.values().stream()
                            .filter(AgentProductVariantDetailsResult::hasText)
                            .forEach(value -> group.add(value, true, null));
                });
        variants.forEach(variant -> addVariantOptions(groups, variant));
        selected.forEach(option -> addOption(groups, option.name(), option.value()));

        RehydratedProductDetails.Variant selectedVariant = details.selectedVariant();
        boolean completeVariantSet = details.totalVariants() != null
                && details.totalVariants() <= variants.size();
        return new AgentProductVariantDetailsResult(
                groups.values().stream()
                        .map(group -> group.result(variants, selected, completeVariantSet))
                        .toList(),
                selected.stream()
                        .filter(option -> hasText(option.name()) && hasText(option.value()))
                        .map(option -> new SelectedOption(option.name().trim(), option.value().trim()))
                        .toList(),
                selectedVariant == null ? null : trimToNull(selectedVariant.title()),
                selectedVariant == null ? null : selectedVariant.available(),
                variants.size(),
                details.totalVariants()
        );
    }

    private static List<RehydratedProductDetails.SelectedOption> selectedOptions(
            RehydratedProductDetails details
    ) {
        List<RehydratedProductDetails.SelectedOption> selected = !details.selected().isEmpty()
                ? details.selected()
                : details.selectedVariant() == null
                ? List.of()
                : details.selectedVariant().selectedOptions();
        return selected.stream().filter(Objects::nonNull).toList();
    }

    private static void addVariantOptions(
            Map<String, OptionGroupBuilder> groups,
            RehydratedProductDetails.Variant variant
    ) {
        variant.selectedOptions().stream()
                .filter(Objects::nonNull)
                .forEach(option -> addOption(groups, option.name(), option.value()));
    }

    private static void addOption(
            Map<String, OptionGroupBuilder> groups,
            String name,
            String value
    ) {
        if (!hasText(name) || !hasText(value)) {
            return;
        }
        groups.computeIfAbsent(
                        normalized(name),
                        ignored -> new OptionGroupBuilder(name.trim()))
                .add(value, true, null);
    }

    private static boolean matches(
            RehydratedProductDetails.Variant variant,
            String optionName,
            String optionValue
    ) {
        return variant.selectedOptions().stream()
                .filter(Objects::nonNull)
                .anyMatch(option -> hasText(option.name())
                        && hasText(option.value())
                        && option.name().trim().equalsIgnoreCase(optionName)
                        && option.value().trim().equalsIgnoreCase(optionValue));
    }

    private static boolean matchesOtherSelectedOptions(
            RehydratedProductDetails.Variant variant,
            List<RehydratedProductDetails.SelectedOption> selected,
            String currentOptionName
    ) {
        return selected.stream()
                .filter(option -> hasText(option.name()) && hasText(option.value()))
                .filter(option -> !option.name().trim().equalsIgnoreCase(currentOptionName))
                .allMatch(option -> matches(variant, option.name().trim(), option.value().trim()));
    }

    private static Boolean derivedAvailability(
            List<RehydratedProductDetails.Variant> variants,
            boolean completeVariantSet
    ) {
        if (variants.isEmpty()) {
            return null;
        }
        if (variants.stream().anyMatch(variant -> Boolean.TRUE.equals(variant.available()))) {
            return true;
        }
        return completeVariantSet
                && variants.stream().allMatch(variant -> Boolean.FALSE.equals(variant.available()))
                ? false
                : null;
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    public record OptionGroup(String name, List<OptionValue> values) {
        public OptionGroup {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record OptionValue(
            String value,
            Boolean exists,
            Boolean available
    ) {
    }

    public record SelectedOption(String name, String value) {
    }

    private static final class OptionGroupBuilder {
        private final String name;
        private final Map<String, OptionValueBuilder> values = new LinkedHashMap<>();

        private OptionGroupBuilder(String name) {
            this.name = name;
        }

        private void add(String value, Boolean exists, Boolean available) {
            String trimmedValue = value.trim();
            values.computeIfAbsent(
                            normalized(trimmedValue),
                            ignored -> new OptionValueBuilder(trimmedValue))
                    .merge(exists, available);
        }

        private OptionGroup result(
                List<RehydratedProductDetails.Variant> variants,
                List<RehydratedProductDetails.SelectedOption> selected,
                boolean completeVariantSet
        ) {
            List<OptionValue> projected = new ArrayList<>();
            values.values().forEach(value -> {
                List<RehydratedProductDetails.Variant> matching = variants.stream()
                        .filter(variant -> matches(variant, name, value.value))
                        .filter(variant -> matchesOtherSelectedOptions(variant, selected, name))
                        .toList();
                Boolean exists = value.exists;
                if (exists == null && (value.available != null || !matching.isEmpty())) {
                    exists = true;
                }
                Boolean available = value.available == null
                        ? derivedAvailability(matching, completeVariantSet)
                        : value.available;
                if (Boolean.FALSE.equals(exists)) {
                    available = false;
                }
                projected.add(new OptionValue(value.value, exists, available));
            });
            return new OptionGroup(name, projected);
        }
    }

    private static final class OptionValueBuilder {
        private final String value;
        private Boolean exists;
        private Boolean available;

        private OptionValueBuilder(String value) {
            this.value = value;
        }

        private void merge(Boolean nextExists, Boolean nextAvailable) {
            if (exists == null || Boolean.FALSE.equals(nextExists)) {
                exists = nextExists;
            }
            if (available == null || Boolean.FALSE.equals(nextAvailable)) {
                available = nextAvailable;
            }
        }
    }
}
