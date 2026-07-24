package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantProductDetailsLookupContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantLookupServiceTest {

    @Test
    void productDetailsContextCarriesEveryPersistedAliasWithoutChangingCatalogRouting() {
        UUID merchantId = UUID.randomUUID();
        String ucpUrl = "https://profile-source.transport.test/custom-ucp.json";
        String advertised = "https://advertised.transport.test/custom-mcp";
        String profileMcp = "https://profile-mcp.transport.test/legacy-mcp";
        String profileEndpoint = "https://fetched-profile.transport.test/redirected-ucp.json";
        String integrationEndpoint = "https://integration.transport.test/catalog-mcp";
        Merchant merchant = Merchant.builder()
                .id(merchantId)
                .domain("official.example")
                .name("Merchant")
                .description("Description")
                .ucpUrl("  " + ucpUrl + " ")
                .advertisedMcpEndpoint(" " + advertised + " ")
                .profileMcpEndpoint("\t" + profileMcp)
                .profileEndpoint(profileEndpoint + "\n")
                .build();
        MerchantIntegration integration = MerchantIntegration.builder()
                .endpoint(integrationEndpoint)
                .build();
        MerchantRepository merchants = mock(MerchantRepository.class);
        MerchantIntegrationRepository integrations = mock(MerchantIntegrationRepository.class);
        when(merchants.findByIdAndActiveTrue(merchantId)).thenReturn(Optional.of(merchant));
        when(integrations.findByMerchantIdAndProviderAndStatusAndRole(
                merchantId,
                MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationRole.STOREFRONT_CATALOG
        )).thenReturn(List.of(integration));

        MerchantProductDetailsLookupContext context =
                new MerchantLookupService(merchants, integrations).activeProductDetailsContext(merchantId);

        assertThat(context.routingMerchant().domain()).isEqualTo("official.example");
        assertThat(context.routingMerchant().advertisedMcpEndpoint()).isEqualTo(integrationEndpoint);
        assertThat(context.routingMerchant().profileMcpEndpoint()).isNull();
        assertThat(context.technicalEndpointAliases()).containsExactly(
                ucpUrl,
                advertised,
                profileMcp,
                profileEndpoint,
                integrationEndpoint
        );
    }

    @Test
    void productDetailsContextDropsBlankAliasesAndDeduplicatesAfterTrimming() {
        MerchantSemanticSearchResult routingMerchant = new MerchantSemanticSearchResult(
                UUID.randomUUID(),
                "official.example",
                "Merchant",
                "https://integration.transport.test/catalog-mcp",
                null,
                "Merchant",
                1.0d,
                1.0d,
                1
        );

        MerchantProductDetailsLookupContext context = new MerchantProductDetailsLookupContext(
                routingMerchant,
                Arrays.asList(
                        null,
                        " ",
                        "https://advertised.transport.test/mcp",
                        " https://advertised.transport.test/mcp "
                )
        );

        assertThat(context.technicalEndpointAliases())
                .containsExactly("https://advertised.transport.test/mcp");
    }
}
