package com.meant.api.plugin.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.payment.card.CardHandler;
import com.meant.api.plugin.payment.card.dto.CardCredentialDetails;
import com.meant.api.plugin.payment.card.dto.CardCredentialRequest;
import com.meant.api.plugin.payment.card.dto.CardPaymentResult;
import com.meant.api.plugin.payment.common.PaymentHandlerRegistry;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.payment.common.exception.PaymentHandlerException;
import com.meant.api.plugin.payment.common.exception.UnknownPaymentHandlerException;
import com.meant.api.plugin.payment.googlepay.GooglePayHandler;
import com.meant.api.plugin.payment.googlepay.dto.GooglePayCredentialDetails;
import com.meant.api.plugin.payment.googlepay.dto.GooglePayCredentialRequest;
import com.meant.api.plugin.payment.googlepay.dto.GooglePayPaymentResult;
import com.meant.api.plugin.payment.shoppay.ShopPayHandler;
import com.meant.api.plugin.payment.shoppay.dto.ShopPayCredentialDetails;
import com.meant.api.plugin.payment.shoppay.dto.ShopPayCredentialRequest;
import com.meant.api.plugin.payment.shoppay.dto.ShopPayPaymentResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentHandlerTest {

    private static final Instant NOW = Instant.parse("2026-06-30T10:15:30Z");
    private static final Instant ISSUED_AT = NOW.minusSeconds(60);
    private static final Instant EXPIRES_AT = NOW.plusSeconds(240);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shopPayBuildsCredentialWithBindingFieldsAndParsesBoundResult() throws Exception {
        ShopPayHandler handler = new ShopPayHandler(objectMapper);
        ShopPayCredentialRequest request = shopPayRequest();

        PaymentInstrument instrument = handler.buildCredential(request);
        ShopPayPaymentResult result = handler.parseResult(shopPayResult(), request.binding());

        assertThat(instrument.handler()).isEqualTo(ShopPayHandler.HANDLER_NAME);
        assertThat(instrument.amountMinor()).isEqualTo(1299L);
        assertThat(instrument.currency()).isEqualTo("USD");
        assertThat(instrument.credential().token()).isEqualTo("ap2-mandate-token");
        assertThat(instrument.credential().details()).isInstanceOf(ShopPayCredentialDetails.class);
        ShopPayCredentialDetails details = (ShopPayCredentialDetails) instrument.credential().details();
        assertThat(details.binding().merchantDomain()).isEqualTo("merchant.example");
        assertThat(details.binding().pspMerchantDomain()).isEqualTo("pay.shopify.com");
        assertThat(details.binding().amountMinor()).isEqualTo(1299L);
        assertThat(details.binding().currency()).isEqualTo("USD");
        assertThat(instrument.scaLiability().liabilityShifted()).isTrue();
        assertThat(objectMapper.writeValueAsString(instrument)).contains("\"amount\":1299");

        assertThat(result.paymentId()).isEqualTo("shop-pay-payment-1");
        assertThat(result.binding()).isEqualTo(request.binding());
    }

    @Test
    void googlePayBuildsCredentialWithBindingFieldsAndParsesBoundResult() {
        GooglePayHandler handler = new GooglePayHandler(objectMapper);
        GooglePayCredentialRequest request = googlePayRequest();

        PaymentInstrument instrument = handler.buildCredential(request);
        GooglePayPaymentResult result = handler.parseResult(googlePayResult(), request.binding());

        assertThat(instrument.handler()).isEqualTo(GooglePayHandler.HANDLER_NAME);
        assertThat(instrument.amountMinor()).isEqualTo(2599L);
        assertThat(instrument.currency()).isEqualTo("EUR");
        assertThat(instrument.credential().token()).isEqualTo("ap2-google-mandate");
        assertThat(instrument.credential().details()).isInstanceOf(GooglePayCredentialDetails.class);
        GooglePayCredentialDetails details = (GooglePayCredentialDetails) instrument.credential().details();
        assertThat(details.binding().pspMerchantId()).isEqualTo("gateway-merchant-1");
        assertThat(details.binding().pspMerchantDomain()).isEqualTo("pay.google.com");
        assertThat(details.protocolVersion()).isEqualTo("ECv2");
        assertThat(instrument.scaLiability().liableParty()).isEqualTo("issuer");

        assertThat(result.paymentId()).isEqualTo("google-pay-payment-1");
        assertThat(result.googlePayMerchantId()).isEqualTo("google-merchant-1");
        assertThat(result.binding()).isEqualTo(request.binding());
    }

    @Test
    void cardBuildsCredentialWithBindingFieldsAndParsesBoundResult() {
        CardHandler handler = new CardHandler(objectMapper);
        CardCredentialRequest request = cardRequest();

        PaymentInstrument instrument = handler.buildCredential(request);
        CardPaymentResult result = handler.parseResult(cardResult(), request.binding());

        assertThat(instrument.handler()).isEqualTo(CardHandler.HANDLER_NAME);
        assertThat(instrument.amountMinor()).isEqualTo(5200L);
        assertThat(instrument.currency()).isEqualTo("JPY");
        assertThat(instrument.credential().token()).isEqualTo("ap2-card-mandate");
        assertThat(instrument.credential().details()).isInstanceOf(CardCredentialDetails.class);
        CardCredentialDetails details = (CardCredentialDetails) instrument.credential().details();
        assertThat(details.binding().merchantDomain()).isEqualTo("merchant.example");
        assertThat(details.binding().pspMerchantId()).isEqualTo("psp-merchant-1");
        assertThat(details.scaLiability().liabilityShifted()).isTrue();

        assertThat(result.paymentId()).isEqualTo("card-payment-1");
        assertThat(result.networkTransactionId()).isEqualTo("network-txn-1");
        assertThat(result.binding()).isEqualTo(request.binding());
    }

    @Test
    void eachHandlerRejectsStaleCryptograms() {
        assertThatThrownBy(() -> new ShopPayHandler(objectMapper).buildCredential(shopPayRequest(
                NOW.minusSeconds(600),
                NOW.minusSeconds(1)
        ))).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("stale");
        assertThatThrownBy(() -> new GooglePayHandler(objectMapper).buildCredential(googlePayRequest(
                NOW.minusSeconds(600),
                NOW.minusSeconds(1)
        ))).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("stale");
        assertThatThrownBy(() -> new CardHandler(objectMapper).buildCredential(cardRequest(
                NOW.minusSeconds(900),
                NOW.minusSeconds(1)
        ))).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("stale");
    }

    @Test
    void eachHandlerRejectsWrongMerchantOrDomainBinding() {
        ShopPayHandler shopPayHandler = new ShopPayHandler(objectMapper);
        GooglePayHandler googlePayHandler = new GooglePayHandler(objectMapper);
        CardHandler cardHandler = new CardHandler(objectMapper);

        assertThatThrownBy(() -> shopPayHandler.parseResult(
                Map.ofEntries(
                        Map.entry("checkout_id", "checkout-1"),
                        Map.entry("merchant_id", "merchant-1"),
                        Map.entry("merchant_domain", "attacker.example"),
                        Map.entry("shop_pay_merchant_id", "shop-merchant-1"),
                        Map.entry("shop_pay_domain", "pay.shopify.com"),
                        Map.entry("amount", 1299L),
                        Map.entry("currency", "USD"),
                        Map.entry("status", "completed"),
                        Map.entry("payment_id", "shop-pay-payment-1")
                ),
                shopPayRequest().binding()
        )).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("merchant domain binding mismatch");

        assertThatThrownBy(() -> googlePayHandler.parseResult(
                Map.ofEntries(
                        Map.entry("checkout_id", "checkout-1"),
                        Map.entry("merchant_id", "wrong-merchant"),
                        Map.entry("merchant_domain", "merchant.example"),
                        Map.entry("google_pay_merchant_id", "google-merchant-1"),
                        Map.entry("gateway_merchant_id", "gateway-merchant-1"),
                        Map.entry("google_pay_domain", "pay.google.com"),
                        Map.entry("amount", 2599L),
                        Map.entry("currency", "EUR"),
                        Map.entry("status", "completed"),
                        Map.entry("payment_id", "google-pay-payment-1")
                ),
                googlePayRequest().binding()
        )).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("merchant id binding mismatch");

        assertThatThrownBy(() -> cardHandler.parseResult(
                Map.ofEntries(
                        Map.entry("checkout_id", "checkout-1"),
                        Map.entry("merchant_id", "merchant-1"),
                        Map.entry("merchant_domain", "merchant.example"),
                        Map.entry("psp_merchant_id", "psp-merchant-1"),
                        Map.entry("psp_merchant_domain", "evil-psp.example"),
                        Map.entry("amount", 5200L),
                        Map.entry("currency", "JPY"),
                        Map.entry("status", "completed"),
                        Map.entry("payment_id", "card-payment-1")
                ),
                cardRequest().binding()
        )).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("PSP merchant domain binding mismatch");
    }

    @Test
    void eachHandlerRejectsAmountOrCurrencyBindingMismatch() {
        assertThatThrownBy(() -> new ShopPayHandler(objectMapper).parseResult(
                Map.ofEntries(
                        Map.entry("checkout_id", "checkout-1"),
                        Map.entry("merchant_id", "merchant-1"),
                        Map.entry("merchant_domain", "merchant.example"),
                        Map.entry("shop_pay_merchant_id", "shop-merchant-1"),
                        Map.entry("shop_pay_domain", "pay.shopify.com"),
                        Map.entry("amount", 1300L),
                        Map.entry("currency", "USD"),
                        Map.entry("status", "completed"),
                        Map.entry("payment_id", "shop-pay-payment-1")
                ),
                shopPayRequest().binding()
        )).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("amount binding mismatch");

        assertThatThrownBy(() -> new GooglePayHandler(objectMapper).parseResult(
                Map.ofEntries(
                        Map.entry("checkout_id", "checkout-1"),
                        Map.entry("merchant_id", "merchant-1"),
                        Map.entry("merchant_domain", "merchant.example"),
                        Map.entry("google_pay_merchant_id", "google-merchant-1"),
                        Map.entry("gateway_merchant_id", "gateway-merchant-1"),
                        Map.entry("google_pay_domain", "pay.google.com"),
                        Map.entry("amount", 2599L),
                        Map.entry("currency", "USD"),
                        Map.entry("status", "completed"),
                        Map.entry("payment_id", "google-pay-payment-1")
                ),
                googlePayRequest().binding()
        )).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("currency binding mismatch");

        assertThatThrownBy(() -> new CardHandler(objectMapper).parseResult(
                Map.ofEntries(
                        Map.entry("checkout_id", "checkout-1"),
                        Map.entry("merchant_id", "merchant-1"),
                        Map.entry("merchant_domain", "merchant.example"),
                        Map.entry("psp_merchant_id", "psp-merchant-1"),
                        Map.entry("psp_merchant_domain", "psp.example"),
                        Map.entry("amount", 52.00d),
                        Map.entry("currency", "JPY"),
                        Map.entry("status", "completed"),
                        Map.entry("payment_id", "card-payment-1")
                ),
                cardRequest().binding()
        )).isInstanceOf(PaymentHandlerException.class)
                .hasMessageContaining("minor-unit integer");
    }

    @Test
    void registryIndexesHandlersByAdvertisedNamesAndIds() {
        PaymentHandlerRegistry registry = new PaymentHandlerRegistry(List.of(
                new ShopPayHandler(objectMapper),
                new GooglePayHandler(objectMapper),
                new CardHandler(objectMapper)
        ));

        assertThat(registry.handler("com.google.pay")).isInstanceOf(GooglePayHandler.class);
        assertThat(registry.handler("google-pay")).isInstanceOf(GooglePayHandler.class);
        assertThat(registry.handler("shop-pay")).isInstanceOf(ShopPayHandler.class);
        assertThat(registry.handler("card_token")).isInstanceOf(CardHandler.class);
        assertThatThrownBy(() -> registry.handler("paypal"))
                .isInstanceOf(UnknownPaymentHandlerException.class);
    }

    private ShopPayCredentialRequest shopPayRequest() {
        return shopPayRequest(ISSUED_AT, EXPIRES_AT);
    }

    private ShopPayCredentialRequest shopPayRequest(Instant issuedAt, Instant expiresAt) {
        return new ShopPayCredentialRequest(
                "checkout-1",
                "merchant-1",
                "https://merchant.example/checkout",
                "shop-merchant-1",
                "https://pay.shopify.com/session",
                1299L,
                "usd",
                "shop-pay-cryptogram",
                issuedAt,
                expiresAt,
                "ap2-mandate-token",
                NOW
        );
    }

    private GooglePayCredentialRequest googlePayRequest() {
        return googlePayRequest(ISSUED_AT, EXPIRES_AT);
    }

    private GooglePayCredentialRequest googlePayRequest(Instant issuedAt, Instant expiresAt) {
        return new GooglePayCredentialRequest(
                "checkout-1",
                "merchant-1",
                "merchant.example",
                "google-merchant-1",
                "gateway-merchant-1",
                "https://pay.google.com",
                2599L,
                "eur",
                "ECv2",
                "google-pay-signature",
                "google-pay-signed-message",
                "google-pay-cryptogram",
                issuedAt,
                expiresAt,
                "ap2-google-mandate",
                NOW
        );
    }

    private CardCredentialRequest cardRequest() {
        return cardRequest(ISSUED_AT, EXPIRES_AT);
    }

    private CardCredentialRequest cardRequest(Instant issuedAt, Instant expiresAt) {
        return new CardCredentialRequest(
                "checkout-1",
                "merchant-1",
                "merchant.example",
                "psp-merchant-1",
                "https://psp.example/pay",
                5200L,
                "jpy",
                "card-token-1",
                "network-txn-1",
                "three-ds-server-txn-1",
                "05",
                "card-cryptogram",
                issuedAt,
                expiresAt,
                "ap2-card-mandate",
                NOW,
                true,
                false
        );
    }

    private Map<String, Object> shopPayResult() {
        return Map.ofEntries(
                Map.entry("checkout_id", "checkout-1"),
                Map.entry("merchant_id", "merchant-1"),
                Map.entry("merchant_domain", "merchant.example"),
                Map.entry("shop_pay_merchant_id", "shop-merchant-1"),
                Map.entry("shop_pay_domain", "pay.shopify.com"),
                Map.entry("amount", 1299L),
                Map.entry("currency", "USD"),
                Map.entry("status", "completed"),
                Map.entry("payment_id", "shop-pay-payment-1"),
                Map.entry("shop_pay_charge_id", "shop-pay-charge-1")
        );
    }

    private Map<String, Object> googlePayResult() {
        return Map.ofEntries(
                Map.entry("checkout_id", "checkout-1"),
                Map.entry("merchant_id", "merchant-1"),
                Map.entry("merchant_domain", "merchant.example"),
                Map.entry("google_pay_merchant_id", "google-merchant-1"),
                Map.entry("gateway_merchant_id", "gateway-merchant-1"),
                Map.entry("google_pay_domain", "pay.google.com"),
                Map.entry("amount", 2599L),
                Map.entry("currency", "EUR"),
                Map.entry("status", "completed"),
                Map.entry("payment_id", "google-pay-payment-1")
        );
    }

    private Map<String, Object> cardResult() {
        return Map.ofEntries(
                Map.entry("checkout_id", "checkout-1"),
                Map.entry("merchant_id", "merchant-1"),
                Map.entry("merchant_domain", "merchant.example"),
                Map.entry("psp_merchant_id", "psp-merchant-1"),
                Map.entry("psp_merchant_domain", "psp.example"),
                Map.entry("amount", 5200L),
                Map.entry("currency", "JPY"),
                Map.entry("status", "completed"),
                Map.entry("payment_id", "card-payment-1"),
                Map.entry("network_transaction_id", "network-txn-1")
        );
    }
}
