package com.meant.api.provider.shopify.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ShopifyUcpClientHttpRequestFactoryTest {

    @Test
    void bearerTransportNeverFollowsRedirects() throws Exception {
        InspectableFactory factory = new InspectableFactory();
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://shop.example/api/ucp/mcp")
                .toURL().openConnection();

        factory.prepare(connection);

        assertThat(connection.getInstanceFollowRedirects()).isFalse();
    }

    private static final class InspectableFactory extends ShopifyUcpClientHttpRequestFactory {
        private InspectableFactory() {
            super(Duration.ofSeconds(1), Duration.ofSeconds(1));
        }

        private void prepare(HttpURLConnection connection) throws Exception {
            prepareConnection(connection, "POST");
        }
    }
}
