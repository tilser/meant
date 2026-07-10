package com.meant.api.plugin.transport.client;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;

public final class ShopifyBearerHeader {

    private final String accessToken;
    private final long generation;
    private final AtomicBoolean unauthorizedRefreshAttempted = new AtomicBoolean();

    ShopifyBearerHeader(String accessToken, long generation) {
        this.accessToken = accessToken;
        this.generation = generation;
    }

    public void applyTo(HttpHeaders headers) {
        headers.setBearerAuth(accessToken);
    }

    long generation() {
        return generation;
    }

    boolean claimUnauthorizedRefresh() {
        return unauthorizedRefreshAttempted.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        return "ShopifyBearerHeader[authorization=Bearer [redacted], generation=%d]".formatted(generation);
    }
}
