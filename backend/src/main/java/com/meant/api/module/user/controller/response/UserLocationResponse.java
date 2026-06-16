package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserLocationResult;

public record UserLocationResponse(
        String country,
        String code,
        String city
) {

    public static UserLocationResponse from(UserLocationResult location) {
        if (location == null) {
            return null;
        }
        return new UserLocationResponse(location.country(), location.code(), location.city());
    }
}
