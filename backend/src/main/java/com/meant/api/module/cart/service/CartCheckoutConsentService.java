package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.CreateCheckoutConsentCommand;
import com.meant.api.module.cart.service.dto.CheckoutConsentResult;
import com.meant.api.plugin.checkout.common.service.BuyerConsentService;
import com.meant.api.plugin.checkout.common.service.command.CreateBuyerConsentCommand;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import com.meant.api.plugin.support.UcpMoney;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CartCheckoutConsentService {

    private static final Duration CONSENT_TTL = Duration.ofMinutes(10);

    private final BuyerConsentService buyerConsentService;
    private final ObjectMapper objectMapper;

    public CheckoutConsentResult recordConsent(Cart cart, CreateCheckoutConsentCommand command) {
        if (!StringUtils.hasText(cart.getRawCheckoutResponse())) {
            throw CartException.rejected("Create a checkout session before authorizing checkout.");
        }
        UcpCheckoutResponse response = parseRawCheckout(cart.getRawCheckoutResponse());
        if (response == null) {
            throw CartException.upstream("UCP checkout response was empty");
        }
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout == null) {
            throw CartException.upstream("UCP checkout response did not contain a checkout session");
        }
        String checkoutId = firstText(checkout.id(), cart.getCheckoutId());
        if (!command.checkoutId().trim().equals(checkoutId)) {
            throw CartException.rejected("Checkout consent did not match the active checkout session.");
        }

        String currency = checkout.resolvedCurrency(cart.getCurrency());
        UcpMoney total = checkout.resolvedTotal();
        if (total == null || total.amount() == null) {
            throw CartException.upstream("UCP checkout response did not contain a total amount");
        }
        currency = firstText(total.currency(), currency);
        if (!StringUtils.hasText(currency)) {
            throw CartException.upstream("UCP checkout response did not contain a currency");
        }

        Instant now = Instant.now();
        BuyerConsentArtifact artifact = buyerConsentService.recordConsent(new CreateBuyerConsentCommand(
                command.userId(),
                cart.getMerchantId(),
                checkoutId,
                lineItems(cart, checkout, currency),
                total.amount(),
                currency.toUpperCase(Locale.ROOT),
                checkout.resolvedTaxAmountMinor(),
                shippingAddress(checkout),
                firstText(command.shippingMethod(), checkout.resolvedShippingMethod(), "none"),
                command.paymentInstrumentReference(),
                now,
                now.plus(CONSENT_TTL),
                firstText(command.presentedTermsHash(), hash(cart.getRawCheckoutResponse()))
        ));
        return new CheckoutConsentResult(artifact.consentId(), artifact.expiresAt());
    }

    private UcpCheckoutResponse parseRawCheckout(String rawCheckoutResponse) {
        try {
            return objectMapper.readValue(rawCheckoutResponse, UcpCheckoutResponse.class);
        } catch (JacksonException exception) {
            throw CartException.upstream("UCP checkout response could not be parsed for consent", exception);
        }
    }

    private List<CreateBuyerConsentCommand.LineItem> lineItems(
            Cart cart,
            UcpCheckoutResponse.Checkout checkout,
            String currency
    ) {
        List<CreateBuyerConsentCommand.LineItem> remoteItems = remoteLineItems(checkout, currency);
        return remoteItems.isEmpty() ? cartLineItems(cart, currency) : remoteItems;
    }

    private List<CreateBuyerConsentCommand.LineItem> remoteLineItems(
            UcpCheckoutResponse.Checkout checkout,
            String currency
    ) {
        if (checkout.lineItems().isEmpty()) {
            return List.of();
        }
        List<CreateBuyerConsentCommand.LineItem> items = new ArrayList<>();
        for (UcpCheckoutResponse.CheckoutLineItem line : checkout.lineItems()) {
            if (line == null) {
                continue;
            }
            String lineCurrency = firstText(line.currency(), currency);
            UcpMoney total = line.resolvedTotal(lineCurrency);
            String variantId = line.resolvedVariantId();
            String lineId = line.resolvedId();
            Integer quantity = line.quantity();
            if (!StringUtils.hasText(lineId)
                    || !StringUtils.hasText(variantId)
                    || quantity == null
                    || total == null
                    || total.amount() == null) {
                continue;
            }
            items.add(new CreateBuyerConsentCommand.LineItem(
                    lineId,
                    variantId,
                    quantity,
                    total.amount(),
                    firstText(total.currency(), lineCurrency, currency)
            ));
        }
        return items;
    }

    private List<CreateBuyerConsentCommand.LineItem> cartLineItems(Cart cart, String fallbackCurrency) {
        List<CreateBuyerConsentCommand.LineItem> items = new ArrayList<>();
        for (CartLine line : cart.getLines()) {
            String currency = firstText(line.getCurrency(), fallbackCurrency, cart.getCurrency());
            if (line.getTotalAmount() == null) {
                throw CartException.upstream("Cart line total amount is required for checkout consent");
            }
            Long amount = UcpMoney.minorAmount(line.getTotalAmount(), currency);
            if (amount == null) {
                throw CartException.upstream("Cart line total amount is required for checkout consent");
            }
            items.add(new CreateBuyerConsentCommand.LineItem(
                    line.getRemoteCartLineId(),
                    line.getProductVariantId(),
                    line.getQuantity(),
                    amount,
                    currency
            ));
        }
        return items;
    }

    private BuyerConsentShippingAddress shippingAddress(UcpCheckoutResponse.Checkout checkout) {
        UcpCheckoutResponse.CheckoutAddress address = checkout.resolvedShippingAddress();
        if (address == null) {
            return null;
        }
        BuyerConsentShippingAddress shippingAddress = new BuyerConsentShippingAddress(
                address.streetAddress(),
                address.addressLocality(),
                address.addressRegion(),
                address.postalCode(),
                address.addressCountry()
        );
        if (shippingAddress.isEmpty()) {
            return null;
        }
        return shippingAddress;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw CartException.upstream("SHA-256 hash algorithm is unavailable", exception);
        }
    }
}
