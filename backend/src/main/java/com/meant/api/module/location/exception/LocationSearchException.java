package com.meant.api.module.location.exception;

public class LocationSearchException extends RuntimeException {

    public LocationSearchException(String message) {
        super(message);
    }

    public LocationSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
