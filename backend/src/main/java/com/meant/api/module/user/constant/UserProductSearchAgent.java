package com.meant.api.module.user.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserProductSearchAgent {
    DISCOVERY("discovery"),
    CURATOR("curator"),
    SEARCH("search");

    private final String value;
}
