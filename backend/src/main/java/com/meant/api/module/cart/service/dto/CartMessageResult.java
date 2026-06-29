package com.meant.api.module.cart.service.dto;

import com.meant.api.plugin.cart.common.dto.UcpCartResponse;

public record CartMessageResult(
        String code,
        String severity,
        String type,
        String message,
        String target
) {

    public static CartMessageResult from(UcpCartResponse.CartMessage message) {
        if (message == null) {
            return null;
        }
        return new CartMessageResult(
                message.code(),
                message.severity(),
                message.type(),
                message.message(),
                message.target()
        );
    }
}
