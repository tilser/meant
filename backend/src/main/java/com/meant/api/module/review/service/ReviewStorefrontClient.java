package com.meant.api.module.review.service;

import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import java.net.URI;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ReviewStorefrontClient {

    private final RestClient restClient;

    public ReviewStorefrontClient(
            RestClient.Builder restClientBuilder,
            ReviewProviderDiscoveryProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.storefrontTimeout());
        requestFactory.setReadTimeout(properties.storefrontTimeout());
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    ReviewStorefrontClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public StorefrontDocument fetch(URI uri) {
        String html = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        return new StorefrontDocument(uri.toString(), html == null ? "" : html);
    }
}
