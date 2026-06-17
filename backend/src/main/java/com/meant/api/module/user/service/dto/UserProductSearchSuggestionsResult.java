package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserProductSearchSuggestionsResult(
        List<String> suggestions
) {
}
