package com.meant.api.module.user.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecision;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionOutcome;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionReason;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserGroupedProductSearchV1ResponseTest {

    @Test
    void mapsServiceContractsToSeparateVersionedControllerRecords() {
        UUID integrationId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        ProviderIdentity provider = new ProviderIdentity("future_provider");
        ExternalIdentifier merchant = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, provider.value(), "Merchant-1");
        ExternalIdentifier product = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), "Product-1");
        ResultSourceReference source = new ResultSourceReference(
                ResultSourceType.PROVIDER_CATALOG, "fixture", null);
        ResultProvenance provenance = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(provider, ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG"),
                new LocalMerchantRouting(integrationId),
                merchant,
                product,
                null,
                new ResultFreshness(Instant.parse("2026-07-10T10:00:00Z"), null),
                source
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        provider,
                        OfferMerchantScope.external(merchant),
                        product,
                        null,
                        List.of(
                                new ProductAttribute("variant-option", "Size", "M"),
                                new ProductAttribute("variant-option", "Color", "Blue")
                        ),
                        List.of(),
                        null
                ),
                "Future merchant",
                null,
                new Money(1234, "eur"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(),
                null,
                List.of(provenance)
        );
        CanonicalProduct canonicalProduct = new CanonicalProduct(
                "product_v1_fixture",
                "Future-provider product",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );

        UserGroupedProductSearchV1Response response = UserGroupedProductSearchV1Response.from(
                new UserGroupedProductSearchResult(
                        "product", "product", "profile", false, 0, 20, null, false, false,
                        List.of(canonicalProduct),
                        Map.of(canonicalProduct.key(), new ProductRankingExplanation(
                                "product-v1",
                                "source-merchant-window-v1",
                                8_000,
                                1,
                                ProductRankingExplanation.Execution.DETERMINISTIC,
                                ProductRankingExplanation.DiversityDecision.NONE,
                                canonicalProduct.key(),
                                List.of(ProductRankingExplanation.Feature.available(
                                        ProductRankingExplanation.Name.LEXICAL_INTENT_FIT,
                                        8_000,
                                        30,
                                        List.of()
                                ))
                        )),
                        Map.of(offer.key(), new OfferRankingExplanation(
                                "offer-v1",
                                9_000,
                                1,
                                OfferRankingExplanation.CommercialTieBreakPolicy.NONE,
                                offer.key(),
                                List.of(OfferRankingExplanation.Feature.available(
                                        OfferRankingExplanation.Name.AVAILABILITY,
                                        10_000,
                                        30
                                ))
                        )),
                        1,
                        false,
                        List.of(new ProductGroupingDecision(
                                offer.key(),
                                offer.key(),
                                ProductGroupingDecisionOutcome.GROUPED,
                                ProductGroupingDecisionReason.EXACT_OFFER,
                                10_000,
                                List.of(),
                                List.of()
                        ))
                )
        );

        assertThat(response.products()).singleElement().satisfies(mappedProduct -> {
            assertThat(mappedProduct.key()).isEqualTo("product_v1_fixture");
            assertThat(mappedProduct.rankingExplanation().rankingVersion()).isEqualTo("product-v1");
            assertThat(mappedProduct.offers()).singleElement().satisfies(mappedOffer -> {
                assertThat(mappedOffer.key()).isEqualTo(offer.key());
                assertThat(mappedOffer.price().minorUnits()).isEqualTo(1234);
                assertThat(mappedOffer.price().currency()).isEqualTo("EUR");
                assertThat(mappedOffer.selectedOptions()).extracting(
                        UserGroupedProductSearchV1Response.ProductAttributeResponse::name,
                        UserGroupedProductSearchV1Response.ProductAttributeResponse::value
                ).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Color", "Blue"),
                        org.assertj.core.groups.Tuple.tuple("Size", "M")
                );
                assertThat(mappedOffer.provenance().getFirst().provider()).isEqualTo("FUTURE_PROVIDER");
                assertThat(mappedOffer.identity().merchantIntegrationId()).isNull();
                assertThat(mappedOffer.identity().merchantScope().externalMerchantIdentity().value())
                        .isEqualTo("Merchant-1");
                assertThat(mappedOffer.provenance().getFirst().merchantIntegrationId()).isEqualTo(integrationId);
                assertThat(mappedOffer.provenance().getFirst().discoverySource().value())
                        .isEqualTo("GLOBAL_CATALOG");
                assertThat(mappedOffer.provenance().getFirst().localRouting().merchantIntegrationId())
                        .isEqualTo(integrationId);
                assertThat(mappedOffer.rankingExplanation().commercialTieBreakPolicy())
                        .isEqualTo(OfferRankingExplanation.CommercialTieBreakPolicy.NONE);
            });
        });
        assertThat(response.groupingDecisions()).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(ProductGroupingDecisionOutcome.GROUPED);
            assertThat(decision.reason()).isEqualTo(ProductGroupingDecisionReason.EXACT_OFFER);
            assertThat(decision.leftOfferKey()).isEqualTo(offer.key());
        });
    }

    @Test
    void everyPublicGroupedResponseFieldDeclaresItsOpenApiRequiredMode() {
        assertRecordSchemas(UserGroupedProductSearchV1Response.class);
        for (Class<?> nested : UserGroupedProductSearchV1Response.class.getDeclaredClasses()) {
            if (nested.isRecord()) {
                assertRecordSchemas(nested);
            }
        }
    }

    private void assertRecordSchemas(Class<?> recordType) {
        assertThat(recordType.getAnnotation(Schema.class))
                .as("record-level @Schema on %s", recordType.getSimpleName())
                .isNotNull();
        for (RecordComponent component : recordType.getRecordComponents()) {
            Schema schema = component.getAccessor().getAnnotation(Schema.class);
            assertThat(schema)
                    .as("@Schema on %s.%s", recordType.getSimpleName(), component.getName())
                    .isNotNull();
            assertThat(schema.requiredMode())
                    .as("requiredMode on %s.%s", recordType.getSimpleName(), component.getName())
                    .isNotEqualTo(Schema.RequiredMode.AUTO);
            assertThat(schema.description())
                    .as("description on %s.%s", recordType.getSimpleName(), component.getName())
                    .isNotBlank();
        }
    }
}
