package com.meant.api.plugin.catalog;

public class UcpCatalogResponseException extends RuntimeException {

    public UcpCatalogResponseException(String message) {
        super(message);
    }

    public UcpCatalogResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
