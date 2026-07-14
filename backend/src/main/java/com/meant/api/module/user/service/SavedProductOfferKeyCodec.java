package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserSavedProduct;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Versioned handle bound to the exact durable reference stored for a saved product. */
public final class SavedProductOfferKeyCodec {
    public static final String PREFIX = "saved_offer_v1_";
    private static final int UUID_LENGTH = 36;
    private static final int FINGERPRINT_LENGTH = 43;

    private SavedProductOfferKeyCodec() {
    }

    public static String encode(UserSavedProduct savedProduct) {
        if (savedProduct == null || savedProduct.getId() == null) {
            throw new IllegalArgumentException("Saved product and id are required");
        }
        return PREFIX + savedProduct.getId() + "_" + fingerprint(savedProduct);
    }

    public static Optional<Selection> decode(String offerKey) {
        if (offerKey == null || !offerKey.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String encoded = offerKey.substring(PREFIX.length());
        if (encoded.length() != UUID_LENGTH + 1 + FINGERPRINT_LENGTH
                || encoded.charAt(UUID_LENGTH) != '_') {
            return Optional.empty();
        }
        String idValue = encoded.substring(0, UUID_LENGTH);
        String referenceFingerprint = encoded.substring(UUID_LENGTH + 1);
        if (!referenceFingerprint.matches("[A-Za-z0-9_-]{" + FINGERPRINT_LENGTH + "}")) {
            return Optional.empty();
        }
        try {
            UUID savedProductId = UUID.fromString(idValue);
            if (!savedProductId.toString().equals(idValue)) {
                return Optional.empty();
            }
            return Optional.of(new Selection(savedProductId, referenceFingerprint));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static boolean verify(Selection selection, UserSavedProduct savedProduct) {
        if (selection == null || savedProduct == null || savedProduct.getId() == null
                || !selection.savedProductId().equals(savedProduct.getId())) {
            return false;
        }
        return MessageDigest.isEqual(
                selection.referenceFingerprint().getBytes(StandardCharsets.US_ASCII),
                fingerprint(savedProduct).getBytes(StandardCharsets.US_ASCII)
        );
    }

    public static String fingerprint(UserSavedProduct savedProduct) {
        if (savedProduct == null) {
            throw new IllegalArgumentException("Saved product is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeField(output, "saved-product-reference-v1");
                writeField(output, value(savedProduct.getUserId()));
                writeField(output, savedProduct.getProductKey());
                writeField(output, savedProduct.getSourceProvider());
                writeField(output, savedProduct.getSourceType());
                writeField(output, savedProduct.getSourceIdentity());
                writeField(output, value(savedProduct.getLocalMerchantId()));
                writeField(output, value(savedProduct.getMerchantIntegrationId()));
                writeField(output, savedProduct.getExternalMerchantId());
                writeField(output, savedProduct.getExternalMerchantDomain());
                writeField(output, savedProduct.getExternalProductId());
                writeField(output, savedProduct.getExternalVariantId());
                writeField(output, savedProduct.getSelectedOptionsJson());
                writeField(output, savedProduct.getComponentsJson());
                writeField(output, savedProduct.getSellingPlanJson());
                writeField(output, savedProduct.getRetentionPolicyKey());
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode saved-product reference", exception);
        }
    }

    private static String value(UUID value) {
        return value == null ? null : value.toString();
    }

    private static void writeField(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    public record Selection(UUID savedProductId, String referenceFingerprint) {
    }
}
