package com.meant.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.service.dto.AgentCanonicalProductArtifact;
import com.meant.api.module.agent.service.dto.AgentSavedProductArtifact;
import com.meant.api.module.agent.service.dto.AgentSavedProductOfferResult;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.merchant.controller.response.MerchantCatalogSearchAttemptResponse;
import com.meant.api.module.merchant.controller.response.MerchantListItemResponse;
import com.meant.api.module.merchant.controller.response.MerchantProductDetailsResponse;
import com.meant.api.module.merchant.controller.response.MerchantSemanticProductResponse;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response;
import com.meant.api.module.user.controller.response.UserInventoryItemResponse;
import com.meant.api.module.user.controller.response.UserProductSearchProductResponse;
import com.meant.api.module.user.controller.response.UserSavedProductResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuyerFacingTransportEndpointResponseTest {

    @Test
    void buyerFacingResponsesDoNotExposeTransportEndpointFields() {
        assertThat(componentNames(CartResponse.class))
                .contains("merchantDomain")
                .doesNotContain("endpoint");
        assertThat(componentNames(MerchantListItemResponse.class))
                .contains("domain")
                .doesNotContain("advertisedMcpEndpoint", "profileMcpEndpoint");
        assertThat(componentNames(MerchantCatalogSearchAttemptResponse.class))
                .contains("domain")
                .doesNotContain("endpoint");
        assertThat(componentNames(MerchantSemanticProductResponse.class))
                .contains("merchantDomain")
                .doesNotContain("endpoint");
        assertThat(componentNames(MerchantProductDetailsResponse.class))
                .doesNotContain("endpoint");
        assertThat(componentNames(UserProductSearchProductResponse.class))
                .contains("merchantDomain")
                .doesNotContain("endpoint");
        assertThat(componentNames(UserGroupedProductSearchV1Response.ResultSourceReferenceResponse.class))
                .doesNotContain("uri");
        assertThat(componentNames(UserGroupedProductSearchV1Response.OfferResponse.class))
                .contains("merchantOrigin")
                .doesNotContain("merchantDomain", "endpoint");
        assertThat(componentNames(UserGroupedProductSearchV1Response.ResultProvenanceResponse.class))
                .doesNotContain("externalMerchantDomain");
        assertThat(componentNames(UserInventoryItemResponse.CommerceReference.class))
                .contains("merchantOrigin")
                .doesNotContain("externalMerchantDomain");
        assertThat(componentNames(UserSavedProductResponse.SavedOffer.class))
                .contains("offerKey", "merchantOrigin")
                .doesNotContain("merchantDomain");
        assertThat(componentNames(AgentCanonicalProductArtifact.AgentResultProvenanceArtifact.class))
                .doesNotContain("externalMerchantDomain");
        assertThat(componentNames(AgentCanonicalProductArtifact.AgentCanonicalOfferArtifact.class))
                .contains("merchantOrigin")
                .doesNotContain("merchantDomain", "endpoint");
        assertThat(componentNames(AgentSavedProductOfferResult.class))
                .contains("offerKey", "merchantOrigin")
                .doesNotContain("merchantDomain");
        assertThat(componentNames(AgentSavedProductArtifact.class))
                .doesNotContain("externalMerchantDomain", "merchantDomain");
    }

    private List<String> componentNames(Class<?> responseType) {
        return Arrays.stream(responseType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
