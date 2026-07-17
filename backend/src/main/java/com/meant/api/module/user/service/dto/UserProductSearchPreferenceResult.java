package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import java.util.List;

public record UserProductSearchPreferenceResult(
        String scope,
        UserProductSearchAttributeName attributeName,
        List<String> values
) {

    public UserProductSearchPreferenceResult {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
