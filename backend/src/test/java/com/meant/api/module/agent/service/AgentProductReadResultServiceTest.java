package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductVariantDetailsResult;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentProductReadResultServiceTest {

    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");

    @Test
    void projectsSelectableVariantValuesAndDerivesAvailabilityFromReturnedVariants() {
        RehydratedProductDetails.Variant coral = variant(
                "variant-coral",
                "Wht/Vivid Coral",
                true
        );
        RehydratedProductDetails.Variant black = variant(
                "variant-black",
                "Black/Lemon",
                true
        );
        RehydratedProductDetails details = details(
                List.of(new RehydratedProductDetails.Option(
                        "Colour",
                        List.of("Wht/Vivid Coral", "Black/Lemon", "Black/Orange"),
                        List.of(
                                new RehydratedProductDetails.OptionValue(
                                        "Wht/Vivid Coral",
                                        true,
                                        true
                                ),
                                new RehydratedProductDetails.OptionValue(
                                        "Black/Lemon",
                                        null,
                                        null
                                ),
                                new RehydratedProductDetails.OptionValue(
                                        "Black/Orange",
                                        null,
                                        false
                                )
                        )
                )),
                List.of(new RehydratedProductDetails.SelectedOption(
                        "Colour",
                        "Wht/Vivid Coral"
                )),
                List.of(coral, black),
                5,
                coral
        );

        AgentProductReferenceResult result = new AgentProductReadResultService(
                mock(AgentJsonSupport.class)
        ).reference(product(), 2, details);

        assertThat(result.variantDetails()).isNotNull();
        assertThat(result.variantDetails().optionGroups())
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.name()).isEqualTo("Colour");
                    assertThat(group.values()).extracting(
                            AgentProductVariantDetailsResult.OptionValue::value
                    ).containsExactly("Wht/Vivid Coral", "Black/Lemon", "Black/Orange");
                    assertThat(group.values())
                            .filteredOn(value -> value.value().equals("Black/Lemon"))
                            .singleElement()
                            .satisfies(value -> {
                                assertThat(value.exists()).isTrue();
                                assertThat(value.available()).isTrue();
                            });
                    assertThat(group.values())
                            .filteredOn(value -> value.value().equals("Black/Orange"))
                            .singleElement()
                            .satisfies(value -> {
                                assertThat(value.exists()).isFalse();
                                assertThat(value.available()).isFalse();
                            });
                });
        assertThat(result.variantDetails().selectedOptions())
                .containsExactly(new AgentProductVariantDetailsResult.SelectedOption(
                        "Colour",
                        "Wht/Vivid Coral"
                ));
        assertThat(result.variantDetails().selectedVariantTitle()).isEqualTo("Wht/Vivid Coral");
        assertThat(result.variantDetails().selectedVariantAvailable()).isTrue();
        assertThat(result.variantDetails().returnedVariantCount()).isEqualTo(2);
        assertThat(result.variantDetails().totalVariantCount()).isEqualTo(5);
    }

    @Test
    void preservesUnknownStateWhenVariantEvidenceIsPartialOrMissing() {
        RehydratedProductDetails.OptionValue unknown =
                new RehydratedProductDetails.OptionValue("Black/Lemon", null, null);
        RehydratedProductDetails missingVariants = details(
                List.of(new RehydratedProductDetails.Option(
                        "Colour",
                        List.of(),
                        List.of(unknown)
                )),
                List.of(),
                List.of(),
                5,
                null
        );
        RehydratedProductDetails partialVariants = details(
                List.of(new RehydratedProductDetails.Option(
                        "Colour",
                        List.of("Black/Lemon"),
                        List.of(unknown)
                )),
                List.of(new RehydratedProductDetails.SelectedOption("Colour", "Black/Lemon")),
                List.of(variant("variant-black", "Black/Lemon", false)),
                5,
                null
        );

        AgentProductVariantDetailsResult missing =
                AgentProductVariantDetailsResult.from(missingVariants);
        AgentProductVariantDetailsResult partial =
                AgentProductVariantDetailsResult.from(partialVariants);

        assertThat(missing.optionGroups().getFirst().values().getFirst())
                .extracting(
                        AgentProductVariantDetailsResult.OptionValue::exists,
                        AgentProductVariantDetailsResult.OptionValue::available
                )
                .containsExactly(null, null);
        assertThat(partial.optionGroups().getFirst().values().getFirst())
                .extracting(
                        AgentProductVariantDetailsResult.OptionValue::exists,
                        AgentProductVariantDetailsResult.OptionValue::available
                )
                .containsExactly(true, null);
    }

    @Test
    void derivesAvailabilityAgainstTheOtherSelectedOptions() {
        RehydratedProductDetails.SelectedOption black =
                new RehydratedProductDetails.SelectedOption("Colour", "Black/Lemon");
        RehydratedProductDetails.SelectedOption white =
                new RehydratedProductDetails.SelectedOption("Colour", "White/Silver");
        RehydratedProductDetails.SelectedOption size =
                new RehydratedProductDetails.SelectedOption("Size", "9.5");
        RehydratedProductDetails.Variant blackVariant = variant(
                "variant-black",
                "Black/Lemon / 9.5",
                false,
                List.of(black, size)
        );
        RehydratedProductDetails.Variant whiteVariant = variant(
                "variant-white",
                "White/Silver / 9.5",
                true,
                List.of(white, size)
        );
        RehydratedProductDetails details = details(
                List.of(
                        new RehydratedProductDetails.Option(
                                "Colour",
                                List.of("Black/Lemon", "White/Silver")
                        ),
                        new RehydratedProductDetails.Option("Size", List.of("9.5"))
                ),
                List.of(black, size),
                List.of(blackVariant, whiteVariant),
                2,
                blackVariant
        );

        AgentProductVariantDetailsResult result =
                AgentProductVariantDetailsResult.from(details);

        assertThat(result.optionGroups())
                .filteredOn(group -> group.name().equals("Size"))
                .singleElement()
                .satisfies(group -> assertThat(group.values().getFirst().available()).isFalse());
    }

    private RehydratedProductDetails.Variant variant(
            String id,
            String colour,
            boolean available
    ) {
        return variant(
                id,
                colour,
                available,
                List.of(new RehydratedProductDetails.SelectedOption("Colour", colour))
        );
    }

    private RehydratedProductDetails.Variant variant(
            String id,
            String title,
            Boolean available,
            List<RehydratedProductDetails.SelectedOption> selectedOptions
    ) {
        return new RehydratedProductDetails.Variant(
                id,
                null,
                title,
                null,
                null,
                "92.00",
                "USD",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                available,
                selectedOptions,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private RehydratedProductDetails details(
            List<RehydratedProductDetails.Option> options,
            List<RehydratedProductDetails.SelectedOption> selected,
            List<RehydratedProductDetails.Variant> variants,
            Integer totalVariants,
            RehydratedProductDetails.Variant selectedVariant
    ) {
        return new RehydratedProductDetails(
                "product-1",
                null,
                "LETHAL SPEED RS MENS FOOTBALL",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                options,
                selected,
                variants,
                totalVariants,
                null,
                null,
                false,
                selectedVariant,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "Manning Shoes"
        );
    }

    private CanonicalProduct product() {
        Instant observedAt = Instant.parse("2026-07-23T10:00:00Z");
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "manning-shoes");
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "product-1");
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "variant-coral");
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                new DiscoverySourceIdentity(
                        PROVIDER,
                        ResultSourceType.PROVIDER_CATALOG,
                        "SHOPIFY_GLOBAL"
                ),
                null,
                merchant,
                "manningshoes.myshopify.com",
                product,
                variant,
                new ResultFreshness(observedAt, observedAt.plusSeconds(300)),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "SHOPIFY_GLOBAL",
                        null
                )
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(new ProductAttribute(
                                "variant-option",
                                "Colour",
                                "Wht/Vivid Coral"
                        )),
                        List.of(),
                        null
                ),
                "Manning Shoes",
                "Wht/Vivid Coral",
                null,
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                "canonical:lethal-speed-rs",
                "LETHAL SPEED RS MENS FOOTBALL",
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
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
