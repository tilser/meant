package com.meant.api.provider.geonames;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.location.exception.InvalidLocationException;
import com.meant.api.module.location.exception.LocationSearchException;
import com.meant.api.module.location.service.dto.LocationSuggestion;
import com.meant.api.module.location.service.port.LocationSearchProvider;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GeoNamesLocationProvider implements LocationSearchProvider {

    private static final String ID_PREFIX = "geonames:";
    private static final Set<String> EXCLUDED_POPULATED_PLACE_CODES = Set.of("PPLH", "PPLQ", "PPLW");

    private final RestClient restClient;
    private final GeoNamesProperties properties;
    private final Cache<SearchKey, List<LocationSuggestion>> searchCache;
    private final Cache<ResolveKey, LocationSuggestion> resolveCache;

    public GeoNamesLocationProvider(RestClient.Builder restClientBuilder, GeoNamesProperties properties) {
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder")
                .clone()
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.properties = Objects.requireNonNull(properties, "properties");
        this.searchCache = cache(properties);
        this.resolveCache = cache(properties);
    }

    @Override
    public List<LocationSuggestion> search(String query, int limit, String language) {
        requireConfigured();
        SearchKey key = new SearchKey(
                query.trim().toLowerCase(Locale.ROOT),
                limit,
                language.trim().toLowerCase(Locale.ROOT)
        );
        return searchCache.get(key, this::loadSearch);
    }

    @Override
    public LocationSuggestion resolve(String id, String language) {
        requireConfigured();
        long geonameId = geonameId(id);
        ResolveKey key = new ResolveKey(geonameId, language.trim().toLowerCase(Locale.ROOT));
        LocationSuggestion location = resolveCache.get(key, this::loadLocation);
        if (location == null) {
            throw new InvalidLocationException("GeoNames location was not found: " + id);
        }
        return location;
    }

    private List<LocationSuggestion> loadSearch(SearchKey key) {
        try {
            GeoNamesSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/searchJSON")
                            .queryParam("name_startsWith", key.query())
                            .queryParam("featureClass", "P")
                            .queryParam("orderby", "relevance")
                            .queryParam("maxRows", key.limit())
                            .queryParam("lang", key.language())
                            .queryParam("style", "FULL")
                            .queryParam("username", properties.username().trim())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        throw new LocationSearchException(
                                "GeoNames search returned HTTP " + httpResponse.getStatusCode());
                    })
                    .body(GeoNamesSearchResponse.class);
            validateStatus(response == null ? null : response.status());
            if (response == null || response.geonames() == null) {
                throw new LocationSearchException("GeoNames search response was missing results");
            }
            return response.geonames().stream()
                    .map(this::toSuggestion)
                    .filter(Objects::nonNull)
                    .limit(key.limit())
                    .toList();
        } catch (LocationSearchException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LocationSearchException("Could not reach GeoNames", exception);
        }
    }

    private LocationSuggestion loadLocation(ResolveKey key) {
        try {
            GeoNamesPlace place = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/getJSON")
                            .queryParam("geonameId", key.geonameId())
                            .queryParam("lang", key.language())
                            .queryParam("style", "FULL")
                            .queryParam("username", properties.username().trim())
                            .build())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new InvalidLocationException("GeoNames location was not found");
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new LocationSearchException("GeoNames lookup returned HTTP " + response.getStatusCode());
                    })
                    .body(GeoNamesPlace.class);
            if (place != null && place.status() != null) {
                if (place.status().value() == 15) {
                    throw new InvalidLocationException("GeoNames location was not found");
                }
                validateStatus(place.status());
            }
            LocationSuggestion suggestion = toSuggestion(place);
            if (suggestion == null) {
                throw new InvalidLocationException("GeoNames result is not a current populated place");
            }
            return suggestion;
        } catch (InvalidLocationException | LocationSearchException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LocationSearchException("Could not reach GeoNames", exception);
        }
    }

    private LocationSuggestion toSuggestion(GeoNamesPlace place) {
        if (place == null
                || place.geonameId() == null
                || !"P".equalsIgnoreCase(place.featureClass())
                || EXCLUDED_POPULATED_PLACE_CODES.contains(clean(place.featureCode()))
                || !StringUtils.hasText(place.name())
                || !StringUtils.hasText(place.countryName())) {
            return null;
        }
        String country = CountryCodeNormalizer.normalizeAlpha2(place.countryCode());
        if (country == null) {
            return null;
        }
        return new LocationSuggestion(
                ID_PREFIX + place.geonameId(),
                place.name().trim(),
                clean(place.adminName1()),
                place.countryName().trim(),
                country,
                place.adminCodes1() == null ? null : clean(place.adminCodes1().iso31662()),
                null
        );
    }

    private void validateStatus(GeoNamesStatus status) {
        if (status != null) {
            throw new LocationSearchException(
                    "GeoNames rejected the request with status %s: %s"
                            .formatted(status.value(), status.message()));
        }
    }

    private long geonameId(String id) {
        if (id == null || !id.startsWith(ID_PREFIX)) {
            throw new InvalidLocationException("Unsupported location ID");
        }
        try {
            long value = Long.parseLong(id.substring(ID_PREFIX.length()));
            if (value <= 0) {
                throw new InvalidLocationException("Invalid GeoNames location ID");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new InvalidLocationException("Invalid GeoNames location ID");
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new LocationSearchException("GeoNames username is not configured");
        }
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static <K, V> Cache<K, V> cache(GeoNamesProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.maximumCacheEntries())
                .expireAfterWrite(properties.cacheTtl())
                .build();
    }

    private record SearchKey(String query, int limit, String language) {
    }

    private record ResolveKey(long geonameId, String language) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesSearchResponse(List<GeoNamesPlace> geonames, GeoNamesStatus status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesPlace(
            Long geonameId,
            String name,
            String countryName,
            String countryCode,
            String adminName1,
            GeoNamesAdminCodes adminCodes1,
            @JsonProperty("fcl") String featureClass,
            @JsonProperty("fcode") String featureCode,
            GeoNamesStatus status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesAdminCodes(@JsonProperty("ISO3166_2") String iso31662) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoNamesStatus(String message, int value) {
    }
}
