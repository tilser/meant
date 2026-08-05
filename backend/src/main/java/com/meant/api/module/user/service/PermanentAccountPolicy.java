package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.PermanentAccountRequiredException;
import com.meant.api.module.user.service.dto.AuthenticatedUser;

public final class PermanentAccountPolicy {

    private PermanentAccountPolicy() {
    }

    public static void requirePermanentAccount(AuthenticatedUser user) {
        if (user.anonymous()) {
            throw new PermanentAccountRequiredException();
        }
    }
}
