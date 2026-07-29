package com.meant.api.module.user.constant;

/** Origin claimed for a qualified value or an explicit no-preference decision. */
public enum UserProductSearchDecisionSource {
    NONE,
    SYSTEM_POLICY,
    ORIGINAL_QUERY,
    CURRENT_USER_TURN,
    CONVERSATION,
    PROFILE,
    DURABLE_PREFERENCE
}
