package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileResponse;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class UcpProfileClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public UcpProfile fetchProfile(String ucpUrl) {
        URI originalUri = URI.create(ucpUrl);
        MerchantEnrichmentException lastFailure = null;
        for (URI candidateUri : candidateUris(originalUri)) {
            try {
                String body = fetchBody(candidateUri);
                if (body == null || body.isBlank()) {
                    lastFailure = new MerchantEnrichmentException("UCP profile response was empty");
                    continue;
                }
                return parseBody(body);
            } catch (MerchantEnrichmentException exception) {
                lastFailure = exception;
            } catch (RestClientException exception) {
                lastFailure = new MerchantEnrichmentException("Failed to fetch UCP profile response", exception);
            }
        }

        throw lastFailure == null
                ? new MerchantEnrichmentException("UCP profile response did not contain ucp data")
                : lastFailure;
    }

    private UcpProfile parseBody(String body) {
        try {
            UcpProfileResponse response = objectMapper.readValue(body, UcpProfileResponse.class);
            if (response != null && response.ucp() != null) {
                return response.ucp();
            }

            UcpProfile profile = objectMapper.readValue(body, UcpProfile.class);
            if (profile.version() != null) {
                return profile;
            }
        } catch (JacksonException exception) {
            throw new MerchantEnrichmentException("UCP profile response was not valid UCP JSON", exception);
        }

        throw new MerchantEnrichmentException("UCP profile response did not contain ucp data");
    }

    private String fetchBody(URI uri) {
        return restClientBuilder.build().get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }

    private List<URI> candidateUris(URI uri) {
        Set<URI> uris = new LinkedHashSet<>();
        uris.add(uri);
        addWwwVariant(uri, uris);
        addJsonVariant(uri, uris);
        List.copyOf(uris).forEach(candidateUri -> addJsonVariant(candidateUri, uris));
        return List.copyOf(uris);
    }

    private void addWwwVariant(URI uri, Set<URI> uris) {
        String host = uri.getHost();
        if (host == null || host.isBlank() || host.startsWith("www.")) {
            return;
        }
        uris.add(buildUri(uri, "www." + host, uri.getPath()));
    }

    private void addJsonVariant(URI uri, Set<URI> uris) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith(".json")) {
            return;
        }
        uris.add(buildUri(uri, uri.getHost(), path + ".json"));
    }

    private URI buildUri(URI uri, String host, String path) {
        if (host == null || host.isBlank()) {
            return uri;
        }
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    uri.getFragment()
            );
        } catch (java.net.URISyntaxException exception) {
            throw new MerchantEnrichmentException("Failed to build UCP profile URL variant", exception);
        }
    }
}
