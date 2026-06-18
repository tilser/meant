package com.meant.api.module.cart.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CartNotFoundException extends CartException {

    public CartNotFoundException(String message) {
        super(message);
    }
}
