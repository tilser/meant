package com.meant.api.plugin.checkout.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.checkout.common.entity.BuyerConsent;
import com.meant.api.plugin.checkout.common.repository.BuyerConsentRepository;
import com.meant.api.plugin.checkout.common.service.command.CreateBuyerConsentCommand;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BuyerConsentServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FakeBuyerConsentRepository repository = new FakeBuyerConsentRepository();
    private final BuyerConsentService service = new BuyerConsentService(repository.proxy(), new ObjectMapper());

    @Test
    void recordsConsentArtifactBindingPurchaseTerms() {
        BuyerConsentArtifact artifact = service.recordConsent(new CreateBuyerConsentCommand(
                USER_ID,
                MERCHANT_ID,
                "co_123",
                List.of(new CreateBuyerConsentCommand.LineItem("line-1", "variant-1", 1, 1999L, "usd")),
                1999L,
                "usd",
                200L,
                Map.of("country", "US", "postal_code", "10001"),
                "standard",
                "card-token-raw-value",
                Instant.parse("2026-06-29T12:00:00Z"),
                Instant.parse("2026-06-29T12:10:00Z"),
                "terms-sha256"
        ));

        assertThat(artifact.userId()).isEqualTo(USER_ID);
        assertThat(artifact.merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(artifact.checkoutId()).isEqualTo("co_123");
        assertThat(artifact.totalAmountMinor()).isEqualTo(1999L);
        assertThat(artifact.currency()).isEqualTo("USD");
        assertThat(artifact.taxAmountMinor()).isEqualTo(200L);
        assertThat(artifact.shippingAddress()).containsEntry("postal_code", "10001");
        assertThat(artifact.shippingMethod()).isEqualTo("standard");
        assertThat(artifact.paymentInstrumentHash()).isNotEqualTo("card-token-raw-value");
        assertThat(artifact.presentedTermsHash()).isEqualTo("terms-sha256");
        assertThat(artifact.lineItems()).singleElement().satisfies(lineItem -> {
            assertThat(lineItem.productVariantId()).isEqualTo("variant-1");
            assertThat(lineItem.totalAmountMinor()).isEqualTo(1999L);
            assertThat(lineItem.currency()).isEqualTo("USD");
        });
    }

    private static final class FakeBuyerConsentRepository {

        private final Map<UUID, BuyerConsent> records = new LinkedHashMap<>();

        private BuyerConsentRepository proxy() {
            return (BuyerConsentRepository) Proxy.newProxyInstance(
                    BuyerConsentRepository.class.getClassLoader(),
                    new Class<?>[]{BuyerConsentRepository.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("save".equals(methodName)) {
                            BuyerConsent consent = (BuyerConsent) args[0];
                            records.put(consent.getId(), consent);
                            return consent;
                        }
                        if ("findByIdAndUserId".equals(methodName)) {
                            BuyerConsent consent = records.get(args[0]);
                            return consent != null && consent.getUserId().equals(args[1])
                                    ? Optional.of(consent)
                                    : Optional.empty();
                        }
                        if ("toString".equals(methodName)) {
                            return "FakeBuyerConsentRepository";
                        }
                        throw new UnsupportedOperationException(methodName);
                    }
            );
        }
    }
}
