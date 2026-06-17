package com.meant.api.module.user.exception;

public class UserProductSearchException extends RuntimeException {

    public UserProductSearchException(String message) {
        super(message);
    }

    public UserProductSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
