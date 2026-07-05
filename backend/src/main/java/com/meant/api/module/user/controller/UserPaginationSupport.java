package com.meant.api.module.user.controller;

final class UserPaginationSupport {

    private UserPaginationSupport() {
    }

    static int pageValue(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    static int limitValue(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null) {
            return defaultLimit;
        }
        return Math.max(1, Math.min(limit, maxLimit));
    }
}
