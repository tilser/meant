package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.UcpCapabilityDefinition;
import com.meant.api.module.merchant.service.dto.UcpPaymentHandlerDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpResourceReference;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.merchant.service.dto.UcpVersionRange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MerchantProfileHashService {

    public String hash(UcpProfile profile, MerchantProfileData profileData) {
        StringBuilder builder = new StringBuilder();
        append(builder, profile.version());
        safeMap(profile.supportedVersions()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    append(builder, entry.getKey());
                    append(builder, entry.getValue());
                });

        safeMap(profile.services()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sortedByStableFields(entry.getValue())
                        .forEach(service -> appendService(builder, entry.getKey(), service)));
        safeMap(profile.capabilities()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sortedByStableFields(entry.getValue())
                        .forEach(capability -> appendCapability(builder, entry.getKey(), capability)));
        safeMap(profile.paymentHandlers()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sortedByStableFields(entry.getValue())
                        .forEach(paymentHandler -> appendPaymentHandler(builder, entry.getKey(), paymentHandler)));

        append(builder, profileData.name());
        append(builder, profileData.description());
        append(builder, profileData.about());
        append(builder, profileData.targetAudience());
        append(builder, profileData.profileQuestion());
        append(builder, profileData.profileAnswerRaw());
        appendValues(builder, profileData.categories().stream().map(this::normalizeCategory).sorted().toList());
        appendValues(builder, profileData.popularSearches().stream().sorted().toList());
        return sha256(builder.toString());
    }

    public String normalizeCategory(String category) {
        return category.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private void appendService(StringBuilder builder, String name, UcpServiceDefinition service) {
        append(builder, name);
        append(builder, service.id());
        append(builder, service.version());
        append(builder, resourceUrl(service.spec()));
        append(builder, service.transport());
        append(builder, service.endpoint());
        append(builder, resourceUrl(service.schema()));
    }

    private void appendCapability(StringBuilder builder, String name, UcpCapabilityDefinition capability) {
        append(builder, name);
        append(builder, capability.id());
        append(builder, capability.version());
        append(builder, resourceUrl(capability.spec()));
        append(builder, resourceUrl(capability.schema()));
        appendValues(builder, sorted(capability.extendsCapabilities()));
        append(builder, stableConfig(capability.config()));
        if (capability.requires() != null) {
            appendRange(builder, capability.requires().protocol());
            safeMap(capability.requires().capabilities()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        append(builder, entry.getKey());
                        appendRange(builder, entry.getValue());
                    });
        }
    }

    private void appendPaymentHandler(StringBuilder builder, String name, UcpPaymentHandlerDefinition paymentHandler) {
        append(builder, name);
        append(builder, paymentHandler.id());
        append(builder, paymentHandler.version());
        append(builder, resourceUrl(paymentHandler.spec()));
        append(builder, resourceUrl(paymentHandler.schema()));
    }

    private void appendRange(StringBuilder builder, UcpVersionRange range) {
        if (range == null) {
            append(builder, null);
            append(builder, null);
            return;
        }
        append(builder, range.min());
        append(builder, range.max());
    }

    private List<String> sorted(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private <T> List<T> sortedByStableFields(Collection<T> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::stableText))
                .toList();
    }

    private <T> Map<String, T> safeMap(Map<String, T> values) {
        return values == null ? Map.of() : values;
    }

    private void appendValues(StringBuilder builder, Collection<String> values) {
        sorted(values).forEach(value -> append(builder, value));
    }

    private String resourceUrl(UcpResourceReference reference) {
        return reference == null ? null : reference.url();
    }

    private String stableText(Object value) {
        return value == null ? "" : value.toString();
    }

    private String stableConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        config.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    builder.append(entry.getKey()).append('=');
                    builder.append(stableText(entry.getValue())).append(';');
                });
        return builder.toString();
    }

    private void append(StringBuilder builder, Object value) {
        String text = value == null ? "" : value.toString();
        builder.append(text.length()).append(':').append(text).append('|');
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
