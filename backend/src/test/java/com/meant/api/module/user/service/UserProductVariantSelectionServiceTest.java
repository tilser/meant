package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.CatalogProductDetailService;
import com.meant.api.module.catalog.service.CatalogPurchaseReferencePolicyResolver;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelectionResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserProductVariantSelectionServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL");
    private static final ExternalIdentifier MERCHANT = identifier(ExternalIdentifierType.MERCHANT, "merchant-1");
    private static final ExternalIdentifier PRODUCT = identifier(ExternalIdentifierType.PRODUCT, "product-1");
    private static final ExternalIdentifier ANCHOR_VARIANT =
            identifier(ExternalIdentifierType.VARIANT, "variant-medium");
    private static final ExternalIdentifier SELECTED_VARIANT =
            identifier(ExternalIdentifierType.VARIANT, "variant-large");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-14T08:00:00Z");
    private static final ResultFreshness FRESHNESS =
            new ResultFreshness(OBSERVED_AT, OBSERVED_AT.plusSeconds(120));
    private static final List<ProductAttribute> ANCHOR_OPTIONS = List.of(
            option("Color", "Red"), option("Size", "M"));
    private static final List<ProductAttribute> SELECTED_OPTIONS = List.of(
            option("Color", "Red"), option("Size", "L"));

    private final UserCanonicalProductSessionStore sessionStore =
            new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
    private final UserSavedProductOfferResolutionService savedResolutionService =
            mock(UserSavedProductOfferResolutionService.class);
    private final CatalogProductDetailService detailService = mock(CatalogProductDetailService.class);
    private final UserSettingsService settingsService = mock(UserSettingsService.class);
    private final CatalogPurchaseReferencePolicyResolver purchasePolicy =
            mock(CatalogPurchaseReferencePolicyResolver.class);
    private UserProductVariantSelectionService service;
    private Offer anchorOffer;

    @BeforeEach
    void setUp() {
        anchorOffer = offer(ANCHOR_VARIANT, ANCHOR_OPTIONS, "Medium");
        sessionStore.rememberOffer(USER_ID, "canonical-product", anchorOffer);
        service = new UserProductVariantSelectionService(
                sessionStore,
                savedResolutionService,
                detailService,
                settingsService,
                purchasePolicy,
                List.of()
        );
        when(settingsService.get(any())).thenReturn(null);
        when(purchasePolicy.allows(any())).thenReturn(true);
    }

    @Test
    void registersOnlyTheUniqueCompleteUnrelaxedExactOffer() {
        when(detailService.getDetails(any(), any(), any())).thenAnswer(invocation ->
                exactDetail(invocation.getArgument(0)));

        var result = service.select(profile(USER_ID), command(USER_ID));

        assertThat(result.cartable()).isTrue();
        assertThat(result.selectedOffer()).isNotNull();
        assertThat(result.selectedOffer().identity().externalVariantIdentity()).isEqualTo(SELECTED_VARIANT);
        assertThat(result.selectedOffer().selectedOptions()).containsExactlyElementsOf(SELECTED_OPTIONS);
        assertThat(result.selectedOffer().listPrice()).isEqualTo(new Money(2900, "USD"));
        assertThat(result.details().selected()).extracting(RehydratedProductDetails.SelectedOption::value)
                .containsExactly("Red", "L");
        assertThat(sessionStore.findOffer(USER_ID, result.selectedOffer().key()))
                .get().extracting(UserCanonicalProductSessionStore.OfferEntry::canonicalProductKey)
                .isEqualTo("canonical-product");
        assertThat(sessionStore.findOffer(OTHER_USER_ID, result.selectedOffer().key())).isEmpty();

        ArgumentCaptor<CatalogProductReference> reference = ArgumentCaptor.forClass(CatalogProductReference.class);
        ArgumentCaptor<CatalogProductDetailSelection> selection =
                ArgumentCaptor.forClass(CatalogProductDetailSelection.class);
        verify(detailService).getDetails(reference.capture(), selection.capture(), any());
        assertThat(reference.getValue().externalVariantReference()).isEqualTo(ANCHOR_VARIANT);
        assertThat(selection.getValue().selectedOptions()).containsExactlyElementsOf(SELECTED_OPTIONS);
        assertThat(selection.getValue().preferences()).containsExactly("Size", "Color");
    }

    @Test
    void returnsCurrentDetailButNoOfferForARelaxedSelection() {
        when(detailService.getDetails(any(), any(), any())).thenAnswer(invocation -> {
            CatalogProductDetailResult exact = exactDetail(invocation.getArgument(0));
            return new CatalogProductDetailResult(
                    exact.rehydration(),
                    exact.details(),
                    new CatalogProductDetailSelectionResult(
                            SELECTED_OPTIONS, ANCHOR_OPTIONS, true, true, 1)
            );
        });

        var result = service.select(profile(USER_ID), command(USER_ID));

        assertThat(result.details()).isNotNull();
        assertThat(result.selectedOffer()).isNull();
        assertThat(result.cartable()).isFalse();
        verifyNoInteractions(purchasePolicy);
    }

    @Test
    void returnsTheExactUnavailableOfferButMarksItNonCartable() {
        when(detailService.getDetails(any(), any(), any())).thenAnswer(invocation ->
                exactDetail(invocation.getArgument(0), OfferAvailabilityStatus.OUT_OF_STOCK));

        var result = service.select(profile(USER_ID), command(USER_ID));

        assertThat(result.selectedOffer()).isNotNull();
        assertThat(result.selectedOffer().availability().status())
                .isEqualTo(OfferAvailabilityStatus.OUT_OF_STOCK);
        assertThat(result.cartable()).isFalse();
        assertThat(sessionStore.findOffer(USER_ID, result.selectedOffer().key())).isPresent();
    }

    @Test
    void doesNotIssueOrRegisterAKeyWhenPurchaseIdentityPolicyRejectsTheReference() {
        when(purchasePolicy.allows(any())).thenReturn(false);
        when(detailService.getDetails(any(), any(), any())).thenAnswer(invocation ->
                exactDetail(invocation.getArgument(0)));
        String expectedOfferKey = offer(SELECTED_VARIANT, SELECTED_OPTIONS, "Large").key();

        var result = service.select(profile(USER_ID), command(USER_ID));

        assertThat(result.selectedOffer()).isNull();
        assertThat(result.cartable()).isFalse();
        assertThat(sessionStore.findOffer(USER_ID, expectedOfferKey)).isEmpty();
    }

    @Test
    void rejectsDuplicateOptionNamesCaseInsensitivelyBeforeLookup() {
        SelectUserProductVariantCommand command = new SelectUserProductVariantCommand(
                USER_ID,
                anchorOffer.key(),
                List.of(
                        new SelectUserProductVariantCommand.SelectedOption("Color", "Red"),
                        new SelectUserProductVariantCommand.SelectedOption(" color ", "Blue")
                ),
                null
        );

        assertThatThrownBy(() -> service.select(profile(USER_ID), command))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("duplicate option names");
        verifyNoInteractions(detailService, settingsService);
    }

    @Test
    void forwardsAnEmptySelectionForAProductWithoutConfigurableOptions() {
        when(detailService.getDetails(any(), any(), any())).thenAnswer(invocation -> {
            CatalogProductReference requested = invocation.getArgument(0);
            CatalogProductReference resolved = reference(requested.interactionKey(), SELECTED_VARIANT, List.of());
            RehydratedCommercialFacts facts = new RehydratedCommercialFacts(
                    "Current product",
                    "Example merchant",
                    new Money(2500, "USD"),
                    new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                    SELECTED_VARIANT,
                    List.of(),
                    List.of(),
                    List.of(),
                    FRESHNESS,
                    CommercialFactsFreshness.fromSingleObservation(FRESHNESS)
            );
            return new CatalogProductDetailResult(
                    CatalogProductRehydrationResult.fresh(requested, resolved, facts),
                    details(true),
                    new CatalogProductDetailSelectionResult(List.of(), List.of(), true, false, 1)
            );
        });

        var result = service.select(
                profile(USER_ID),
                new SelectUserProductVariantCommand(USER_ID, anchorOffer.key(), List.of(), null)
        );

        assertThat(result.selectedOffer()).isNotNull();
        ArgumentCaptor<CatalogProductDetailSelection> selection =
                ArgumentCaptor.forClass(CatalogProductDetailSelection.class);
        verify(detailService).getDetails(any(), selection.capture(), any());
        assertThat(selection.getValue().selectedOptions()).isEmpty();
        assertThat(selection.getValue().preferences()).isEmpty();
    }

    @Test
    void anotherUserCannotUseTheLiveAnchor() {
        assertThatThrownBy(() -> service.select(profile(OTHER_USER_ID), command(OTHER_USER_ID)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .satisfies(error -> assertThat(((SelectedOfferResolutionException) error).getFailure())
                        .isEqualTo(SelectedOfferResolutionException.Failure.WRONG_USER));
        verifyNoInteractions(detailService, settingsService);
    }

    @Test
    void rejectsAnUnknownOrExpiredLiveAnchor() {
        SelectUserProductVariantCommand expired = new SelectUserProductVariantCommand(
                USER_ID,
                "expired-offer-key",
                command(USER_ID).selectedOptions(),
                "Size"
        );

        assertThatThrownBy(() -> service.select(profile(USER_ID), expired))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .satisfies(error -> assertThat(((SelectedOfferResolutionException) error).getFailure())
                        .isEqualTo(SelectedOfferResolutionException.Failure.UNKNOWN_OR_EXPIRED));
        verifyNoInteractions(detailService, settingsService);
    }

    @Test
    void rejectsAStaleDurableSavedAnchorBeforeCallingGetProduct() {
        String savedOfferKey = SavedProductOfferKeyCodec.PREFIX
                + "20000000-0000-0000-0000-000000000001_" + "A".repeat(43);
        when(savedResolutionService.selection(
                eq(USER_ID),
                any(SavedProductOfferKeyCodec.Selection.class),
                eq(savedOfferKey)
        )).thenThrow(SelectedOfferResolutionException.rejected(
                SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE,
                "Saved offer fingerprint is stale"
        ));

        assertThatThrownBy(() -> service.select(
                profile(USER_ID),
                new SelectUserProductVariantCommand(
                        USER_ID,
                        savedOfferKey,
                        command(USER_ID).selectedOptions(),
                        "Size"
                )
        )).isInstanceOf(SelectedOfferResolutionException.class)
                .satisfies(error -> assertThat(((SelectedOfferResolutionException) error).getFailure())
                        .isEqualTo(SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE));
        verifyNoInteractions(detailService, settingsService);
    }

    @Test
    void resolvesADurableSavedOfferAsTheSelectionAnchor() {
        String savedOfferKey = SavedProductOfferKeyCodec.PREFIX
                + "20000000-0000-0000-0000-000000000001_" + "A".repeat(43);
        CatalogProductReference savedReference = reference(savedOfferKey, ANCHOR_VARIANT, ANCHOR_OPTIONS);
        when(savedResolutionService.selection(
                eq(USER_ID),
                any(SavedProductOfferKeyCodec.Selection.class),
                eq(savedOfferKey)
        )).thenReturn(new UserSavedProductOfferResolutionService.Selection(
                "saved-product", savedOfferKey, savedReference));
        when(detailService.getDetails(any(), any(), any())).thenAnswer(invocation ->
                exactDetail(invocation.getArgument(0)));

        var result = service.select(
                profile(USER_ID),
                new SelectUserProductVariantCommand(
                        USER_ID,
                        savedOfferKey,
                        List.of(
                                new SelectUserProductVariantCommand.SelectedOption("Color", "Red"),
                                new SelectUserProductVariantCommand.SelectedOption("Size", "L")
                        ),
                        "Size"
                )
        );

        assertThat(result.selectedOffer()).isNotNull();
        assertThat(sessionStore.findOffer(USER_ID, result.selectedOffer().key()))
                .get().extracting(UserCanonicalProductSessionStore.OfferEntry::canonicalProductKey)
                .isEqualTo("saved-product");
    }

    private CatalogProductDetailResult exactDetail(CatalogProductReference requested) {
        return exactDetail(requested, OfferAvailabilityStatus.IN_STOCK);
    }

    private CatalogProductDetailResult exactDetail(
            CatalogProductReference requested,
            OfferAvailabilityStatus availability
    ) {
        CatalogProductReference resolved = reference(
                requested.interactionKey(), SELECTED_VARIANT, SELECTED_OPTIONS);
        RehydratedCommercialFacts facts = new RehydratedCommercialFacts(
                "Current shirt",
                "Example merchant",
                new Money(2500, "USD"),
                new OfferAvailability(availability, availability == OfferAvailabilityStatus.IN_STOCK ? 5 : 0, null),
                SELECTED_VARIANT,
                SELECTED_OPTIONS,
                List.of(),
                List.of(),
                FRESHNESS,
                CommercialFactsFreshness.fromSingleObservation(FRESHNESS)
        );
        return new CatalogProductDetailResult(
                CatalogProductRehydrationResult.fresh(requested, resolved, facts),
                details(availability != OfferAvailabilityStatus.OUT_OF_STOCK
                        && availability != OfferAvailabilityStatus.DISCONTINUED),
                new CatalogProductDetailSelectionResult(
                        SELECTED_OPTIONS, SELECTED_OPTIONS, true, false, 1)
        );
    }

    private RehydratedProductDetails details(boolean available) {
        RehydratedProductDetails.Variant selected = new RehydratedProductDetails.Variant(
                SELECTED_VARIANT.value(),
                "large",
                "Large",
                "Large red shirt",
                "https://merchant.test/products/shirt?variant=large",
                "25.00",
                "USD",
                "29.00",
                "USD",
                "SHIRT-L",
                "https://merchant.test/large.jpg",
                "Large red shirt",
                List.of(),
                available,
                List.of(
                        new RehydratedProductDetails.SelectedOption("Color", "Red"),
                        new RehydratedProductDetails.SelectedOption("Size", "L")
                ),
                List.of(),
                List.of(),
                List.of()
        );
        return new RehydratedProductDetails(
                PRODUCT.value(),
                "shirt",
                "Current shirt",
                "A current shirt",
                "https://merchant.test/products/shirt",
                "https://merchant.test/shirt.jpg",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new RehydratedProductDetails.Option("Color", List.of("Red", "Blue"), List.of(
                                new RehydratedProductDetails.OptionValue("Red", true, true),
                                new RehydratedProductDetails.OptionValue("Blue", true, true)
                        )),
                        new RehydratedProductDetails.Option("Size", List.of("M", "L"), List.of(
                                new RehydratedProductDetails.OptionValue("M", true, true),
                                new RehydratedProductDetails.OptionValue("L", true, true)
                        ))
                ),
                selected.selectedOptions(),
                List.of(selected),
                1,
                new RehydratedProductDetails.PriceRange("25.00", "25.00", "USD"),
                new RehydratedProductDetails.PriceRange("29.00", "29.00", "USD"),
                false,
                selected,
                List.of("SHIRT-L"),
                List.of(),
                List.of("Cotton"),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "Example merchant"
        );
    }

    private SelectUserProductVariantCommand command(UUID userId) {
        return new SelectUserProductVariantCommand(
                userId,
                anchorOffer.key(),
                List.of(
                        new SelectUserProductVariantCommand.SelectedOption("Color", "Red"),
                        new SelectUserProductVariantCommand.SelectedOption("Size", "L")
                ),
                "Size"
        );
    }

    private EnsureUserProfileCommand profile(UUID userId) {
        return new EnsureUserProfileCommand(userId, "shopper@example.com", "Test", "Shopper");
    }

    private Offer offer(ExternalIdentifier variant, List<ProductAttribute> options, String title) {
        return new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(MERCHANT),
                        PRODUCT,
                        variant,
                        options,
                        List.of(),
                        null
                ),
                "Example merchant",
                title,
                new Money(2500, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                List.of(),
                null,
                List.of(new ResultProvenance(
                        PROVIDER,
                        SOURCE,
                        null,
                        MERCHANT,
                        "merchant.test",
                        PRODUCT,
                        variant,
                        FRESHNESS,
                        new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, SOURCE.value(), null)
                ))
        );
    }

    private CatalogProductReference reference(
            String interactionKey,
            ExternalIdentifier variant,
            List<ProductAttribute> options
    ) {
        return new CatalogProductReference(
                interactionKey,
                SOURCE,
                null,
                null,
                MERCHANT,
                "merchant.test",
                PRODUCT,
                variant,
                options
        );
    }

    private static ProductAttribute option(String name, String value) {
        return new ProductAttribute("variant-option", name, value);
    }

    private static ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
