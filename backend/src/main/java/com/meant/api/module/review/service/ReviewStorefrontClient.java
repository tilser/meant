package com.meant.api.module.review.service;

import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ReviewStorefrontClient {

    private final RestClient restClient;
    private final ReviewOutboundUrlValidator outboundUrlValidator;
    private final int storefrontMaxBytes;

    @Autowired
    public ReviewStorefrontClient(
            RestClient.Builder restClientBuilder,
            ReviewProviderDiscoveryProperties properties,
            ReviewOutboundUrlValidator outboundUrlValidator
    ) {
        this.outboundUrlValidator = outboundUrlValidator;
        this.storefrontMaxBytes = properties.storefrontMaxBytes();
        this.restClient = restClientBuilder.clone()
                .requestFactory(new ReviewClientHttpRequestFactory(
                        outboundUrlValidator,
                        properties.storefrontTimeout(),
                        properties.storefrontTimeout()
                ))
                .build();
    }

    ReviewStorefrontClient(RestClient restClient) {
        this(restClient, 2 * 1024 * 1024);
    }

    ReviewStorefrontClient(RestClient restClient, int storefrontMaxBytes) {
        this(restClient, new ReviewOutboundUrlValidator(), storefrontMaxBytes);
    }

    ReviewStorefrontClient(
            RestClient restClient,
            ReviewOutboundUrlValidator outboundUrlValidator,
            int storefrontMaxBytes
    ) {
        this.restClient = restClient;
        this.outboundUrlValidator = outboundUrlValidator;
        this.storefrontMaxBytes = storefrontMaxBytes;
    }

    public StorefrontDocument fetch(URI uri) {
        URI validatedUri = outboundUrlValidator.validateOutboundUrl(uri);
        String html = restClient.get()
                .uri(validatedUri)
                .exchange((_, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new RestClientException("Storefront returned status " + response.getStatusCode());
                    }
                    return limitedBody(response.getBody());
                });
        return new StorefrontDocument(validatedUri.toString(), html == null ? "" : html);
    }

    private String limitedBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(storefrontMaxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > storefrontMaxBytes) {
                throw new RestClientException("Storefront response exceeded " + storefrontMaxBytes + " bytes");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
