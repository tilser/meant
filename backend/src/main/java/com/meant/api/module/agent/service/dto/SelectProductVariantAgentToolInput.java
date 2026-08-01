package com.meant.api.module.agent.service.dto;

import java.util.List;

public record SelectProductVariantAgentToolInput(
        String offerKey,
        List<SelectedOption> selectedOptions,
        String preferredOptionName
) {
    public SelectProductVariantAgentToolInput {
        selectedOptions = selectedOptions == null ? null : List.copyOf(selectedOptions);
    }

    public record SelectedOption(String name, String value) {
    }
}
