package com.meant.api.common.util;

import java.util.List;
import java.util.Objects;

public final class CollectionUtils {

    private CollectionUtils() {
    }

    public static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static <T> List<T> safeNonNullList(List<T> values) {
        return safeList(values).stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
