package com.meant.api.module.user.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UserFederatedProductSearchStreamEventResponseTest {

    @Test
    void sanitizesTransportCoordinatesInBuyerVisibleSourceFailures() {
        CatalogSourceFailure failure = new CatalogSourceFailure(
                CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                "Source seller.myshopify.com failed at /.well-known/ucp.json",
                Duration.ofSeconds(5),
                503
        );

        UserFederatedProductSearchStreamEventResponse.CatalogSourceFailureResponse response =
                UserFederatedProductSearchStreamEventResponse.CatalogSourceFailureResponse.from(failure);

        assertThat(response.message())
                .isEqualTo("Source the merchant failed at the merchant")
                .doesNotContain("myshopify.com", "/.well-known/ucp.json");
    }
}
