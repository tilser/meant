package com.meant.api.plugin.transport.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Dedicated no-redirect factory so an allowlisted Shopify bearer cannot follow to another host. */
final class ShopifyUcpClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    ShopifyUcpClientHttpRequestFactory(Duration connectTimeout, Duration readTimeout) {
        setConnectTimeout(connectTimeout);
        setReadTimeout(readTimeout);
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        super.prepareConnection(connection, httpMethod);
        connection.setInstanceFollowRedirects(false);
    }
}
