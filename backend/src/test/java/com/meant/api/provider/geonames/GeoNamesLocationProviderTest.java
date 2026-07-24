package com.meant.api.provider.geonames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.location.exception.InvalidLocationException;
import com.meant.api.module.location.service.dto.LocationSuggestion;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeoNamesLocationProviderTest {

    @Test
    void searchReturnsCurrentPopulatedPlacesWithUcpLocationCodes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoNamesLocationProvider provider = new GeoNamesLocationProvider(builder, properties());
        server.expect(requestTo("https://secure.geonames.test/searchJSON"
                        + "?name_startsWith=pra&featureClass=P&orderby=relevance&maxRows=8"
                        + "&lang=en&style=FULL&username=test-user"))
                .andExpect(queryParam("featureClass", "P"))
                .andRespond(withSuccess("""
                        {
                          "geonames": [
                            {
                              "geonameId": 3067696,
                              "name": "Prague",
                              "countryName": "Czechia",
                              "countryCode": "CZ",
                              "adminName1": "Prague",
                              "adminCodes1": {"ISO3166_2": "10"},
                              "fcl": "P",
                              "fcode": "PPLC"
                            },
                            {
                              "geonameId": 999,
                              "name": "Prague Historic",
                              "countryName": "Czechia",
                              "countryCode": "CZ",
                              "fcl": "P",
                              "fcode": "PPLH"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<LocationSuggestion> result = provider.search("Pra", 8, "en");

        assertThat(result).containsExactly(new LocationSuggestion(
                "geonames:3067696",
                "Prague",
                "Prague",
                "Czechia",
                "CZ",
                "10",
                null
        ));
        server.verify();
    }

    @Test
    void resolveRejectsAFeatureThatIsNotAPopulatedPlace() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoNamesLocationProvider provider = new GeoNamesLocationProvider(builder, properties());
        server.expect(requestTo("https://secure.geonames.test/getJSON"
                        + "?geonameId=3067696&lang=en&style=FULL&username=test-user"))
                .andRespond(withSuccess("""
                        {
                          "geonameId": 3067696,
                          "name": "Prague",
                          "countryName": "Czechia",
                          "countryCode": "CZ",
                          "fcl": "A",
                          "fcode": "ADM1"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.resolve("geonames:3067696", "en"))
                .isInstanceOf(InvalidLocationException.class);
        server.verify();
    }

    @Test
    void requiresAConfiguredGeoNamesUsernameBeforeCallingTheNetwork() {
        GeoNamesProperties unconfigured = new GeoNamesProperties(
                "",
                URI.create("https://secure.geonames.test"),
                Duration.ofHours(1),
                100
        );
        GeoNamesLocationProvider provider = new GeoNamesLocationProvider(RestClient.builder(), unconfigured);

        assertThatThrownBy(() -> provider.search("Pra", 8, "en"))
                .hasMessageContaining("username is not configured");
    }

    private GeoNamesProperties properties() {
        return new GeoNamesProperties(
                "test-user",
                URI.create("https://secure.geonames.test"),
                Duration.ofHours(1),
                100
        );
    }
}
