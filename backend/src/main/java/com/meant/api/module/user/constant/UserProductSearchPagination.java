package com.meant.api.module.user.constant;

public final class UserProductSearchPagination {

    public static final int DEFAULT_OFFSET = 0;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 20;
    public static final int MAX_RESULT_WINDOW = 100;
    public static final int MAX_OFFSET = MAX_RESULT_WINDOW - 1;

    private UserProductSearchPagination() {
    }
}
