package com.meant.api.plugin.checkout.common.service;

import com.meant.api.plugin.checkout.common.entity.CheckoutCanaryEvent;
import com.meant.api.plugin.checkout.common.entity.CheckoutCanaryOutcome;
import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.common.repository.CheckoutCanaryEventRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class CheckoutCanaryService {

    private final CheckoutCanaryEventRepository repository;

    @Transactional
    public void record(@NotNull @Valid CanaryEventCommand command) {
        repository.save(CheckoutCanaryEvent.builder()
                .merchantId(command.merchantId())
                .checkoutIdHash(hash(command.checkoutId()))
                .outcome(command.outcome())
                .remoteStatus(blankToNull(command.remoteStatus()))
                .errorCode(blankToNull(command.errorCode()))
                .chargeMismatch(command.chargeMismatch())
                .nativeCheckoutEnabled(command.nativeCheckoutEnabled())
                .createdAt(Instant.now())
                .build());
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CanaryEventCommand(
            @NotNull UUID merchantId,
            String checkoutId,
            @NotNull CheckoutCanaryOutcome outcome,
            String remoteStatus,
            String errorCode,
            boolean chargeMismatch,
            boolean nativeCheckoutEnabled
    ) {
    }
}
