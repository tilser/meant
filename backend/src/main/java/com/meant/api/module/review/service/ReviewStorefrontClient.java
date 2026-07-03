package com.meant.api.module.review.service;

import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ReviewStorefrontClient {

    private final RestClient restClient;
    private final ReviewOutboundUrlValidator outboundUrlValidator;

    @Autowired
    public ReviewStorefrontClient(
            RestClient.Builder restClientBuilder,
            ReviewProviderDiscoveryProperties properties,
            ReviewOutboundUrlValidator outboundUrlValidator
    ) {
        this.outboundUrlValidator = outboundUrlValidator;
        this.restClient = restClientBuilder.clone()
                .requestFactory(new ReviewClientHttpRequestFactory(
                        outboundUrlValidator,
                        properties.storefrontTimeout(),
                        properties.storefrontTimeout()
                ))
                .build();
    }

    ReviewStorefrontClient(RestClient restClient) {
        this.restClient = restClient;
        this.outboundUrlValidator = new ReviewOutboundUrlValidator();
    }

    public StorefrontDocument fetch(URI uri) {
        URI validatedUri = outboundUrlValidator.validateOutboundUrl(uri);
        String html = restClient.get()
                .uri(validatedUri)
                .retrieve()
                .body(String.class);
        return new StorefrontDocument(validatedUri.toString(), html == null ? "" : html);
    }
}
