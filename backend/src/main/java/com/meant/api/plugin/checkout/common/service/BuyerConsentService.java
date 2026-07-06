package com.meant.api.plugin.checkout.common.service;

import com.meant.api.plugin.checkout.common.entity.BuyerConsent;
import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.common.repository.BuyerConsentRepository;
import com.meant.api.plugin.checkout.common.service.command.CreateBuyerConsentCommand;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class BuyerConsentService {

    private static final TypeReference<List<BuyerConsentArtifact.LineItem>> LINE_ITEM_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<BuyerConsentShippingAddress> SHIPPING_ADDRESS_TYPE = new TypeReference<>() {
    };

    private final BuyerConsentRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BuyerConsentArtifact recordConsent(@NotNull @Valid CreateBuyerConsentCommand command) {
        BuyerConsent consent = repository.save(BuyerConsent.builder()
                .userId(command.userId())
                .merchantId(command.merchantId())
                .checkoutId(command.checkoutId().trim())
                .checkoutIdHash(hash(command.checkoutId()))
                .lineItemsJson(toJson(lineItems(command.lineItems())))
                .totalAmount(command.totalAmountMinor())
                .currency(command.currency().trim().toUpperCase(java.util.Locale.ROOT))
                .taxAmount(command.taxAmountMinor())
                .shippingAddressJson(command.shippingAddress() == null ? null : toJson(command.shippingAddress()))
                .shippingMethod(command.shippingMethod().trim())
                .paymentInstrumentHash(hash(command.paymentInstrumentReference()))
                .presentedTermsHash(command.presentedTermsHash().trim())
                .consentedAt(command.consentedAt())
                .expiresAt(command.expiresAt())
                .createdAt(Instant.now())
                .build());
        return artifact(consent);
    }

    @Transactional(readOnly = true)
    public BuyerConsentArtifact findArtifact(UUID consentId, UUID userId) {
        BuyerConsent consent = repository.findByIdAndUserId(consentId, userId)
                .orElseThrow(() -> new UcpCheckoutSafetyException("Buyer consent was not found: " + consentId));
        if (!consent.getExpiresAt().isAfter(Instant.now())) {
            throw new UcpCheckoutSafetyException("Buyer consent has expired: " + consentId);
        }
        return artifact(consent);
    }

    private BuyerConsentArtifact artifact(BuyerConsent consent) {
        return new BuyerConsentArtifact(
                consent.getId(),
                consent.getUserId(),
                consent.getMerchantId(),
                consent.getCheckoutId(),
                fromJson(consent.getLineItemsJson(), LINE_ITEM_TYPE),
                consent.getTotalAmount(),
                consent.getCurrency(),
                consent.getTaxAmount(),
                consent.getShippingAddressJson() == null
                        ? null
                        : fromJson(consent.getShippingAddressJson(), SHIPPING_ADDRESS_TYPE),
                consent.getShippingMethod(),
                consent.getPaymentInstrumentHash(),
                consent.getConsentedAt(),
                consent.getExpiresAt(),
                consent.getPresentedTermsHash()
        );
    }

    private List<BuyerConsentArtifact.LineItem> lineItems(List<CreateBuyerConsentCommand.LineItem> lineItems) {
        return lineItems.stream()
                .map(lineItem -> new BuyerConsentArtifact.LineItem(
                        lineItem.id(),
                        lineItem.productVariantId(),
                        lineItem.quantity(),
                        lineItem.totalAmountMinor(),
                        lineItem.currency().trim().toUpperCase(java.util.Locale.ROOT)
                ))
                .toList();
    }

    private String toJson(List<BuyerConsentArtifact.LineItem> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new UcpCheckoutSafetyException("Buyer consent artifact could not be serialized", exception);
        }
    }

    private String toJson(BuyerConsentShippingAddress value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new UcpCheckoutSafetyException("Buyer consent artifact could not be serialized", exception);
        }
    }

    private <T> T fromJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new UcpCheckoutSafetyException("Buyer consent artifact could not be parsed", exception);
        }
    }

    private String hash(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new UcpCheckoutSafetyException("SHA-256 hash algorithm is unavailable", exception);
        }
    }
}
