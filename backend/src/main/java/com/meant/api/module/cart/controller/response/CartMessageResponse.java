package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartMessageResult;

public record CartMessageResponse(
        String code,
        String severity,
        String type,
        String message,
        String target
) {

    public static CartMessageResponse from(CartMessageResult result) {
        if (result == null) {
            return null;
        }
        return new CartMessageResponse(
                result.code(),
                result.severity(),
                result.type(),
                result.message(),
                result.target()
        );
    }
}
