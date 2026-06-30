package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import com.meant.api.module.merchant.service.dto.UcpProfileResponse;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class UcpProfileClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MerchantOutboundUrlValidator merchantOutboundUrlValidator;

    @Autowired
    public UcpProfileClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MerchantOutboundUrlValidator merchantOutboundUrlValidator
    ) {
        this(
                restClientBuilder.clone()
                        .requestFactory(new MerchantClientHttpRequestFactory(merchantOutboundUrlValidator))
                        .build(),
                objectMapper,
                merchantOutboundUrlValidator
        );
    }

    public UcpProfileClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            MerchantOutboundUrlValidator merchantOutboundUrlValidator
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
    }

    public UcpProfileClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this(restClientBuilder, objectMapper, new MerchantOutboundUrlValidator());
    }

    public UcpProfile fetchProfile(String merchantDomain, String ucpUrl) {
        return fetchProfileResult(merchantDomain, ucpUrl).profile();
    }

    public UcpProfileFetchResult fetchProfileResult(String merchantDomain, String ucpUrl) {
        URI originalUri;
        try {
            originalUri = merchantOutboundUrlValidator.validateMerchantUrl(merchantDomain, ucpUrl);
        } catch (MerchantOutboundUrlException exception) {
            throw new MerchantEnrichmentException("Blocked UCP profile URL", exception);
        }
        MerchantEnrichmentException lastFailure = null;
        for (URI candidateUri : candidateUris(originalUri)) {
            try {
                URI validatedUri = merchantOutboundUrlValidator.validateMerchantUrl(merchantDomain, candidateUri);
                String body = fetchBody(validatedUri);
                if (body == null || body.isBlank()) {
                    lastFailure = new MerchantEnrichmentException("UCP profile response was empty");
                    continue;
                }
                return parseBody(body, validatedUri, Instant.now());
            } catch (MerchantOutboundUrlException exception) {
                lastFailure = new MerchantEnrichmentException("Blocked UCP profile URL", exception);
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

    private UcpProfileFetchResult parseBody(String body, URI endpoint, Instant capturedAt) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode ucpNode = root.path("ucp");
            if (!ucpNode.isMissingNode() && !ucpNode.isNull()) {
                UcpProfileResponse response = objectMapper.readValue(body, UcpProfileResponse.class);
                if (response != null && response.ucp() != null) {
                    return new UcpProfileFetchResult(
                            response.ucp(),
                            objectMapper.writeValueAsString(ucpNode),
                            endpoint.toString(),
                            capturedAt
                    );
                }
            }

            UcpProfile profile = objectMapper.readValue(body, UcpProfile.class);
            if (profile.version() != null) {
                return new UcpProfileFetchResult(
                        profile,
                        objectMapper.writeValueAsString(root),
                        endpoint.toString(),
                        capturedAt
                );
            }
        } catch (JacksonException exception) {
            throw new MerchantEnrichmentException("UCP profile response was not valid UCP JSON", exception);
        }

        throw new MerchantEnrichmentException("UCP profile response did not contain ucp data");
    }

    private String fetchBody(URI uri) {
        return restClient.get()
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
