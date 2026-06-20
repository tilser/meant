package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.properties.MerchantIdentityLinkingProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class MerchantIdentityTokenCipher {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION = "v1";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantIdentityTokenCipher(MerchantIdentityLinkingProperties properties) {
        this.key = new SecretKeySpec(sha256(properties.tokenEncryptionSecret()), KEY_ALGORITHM);
    }

    public String encrypt(String plaintext, UUID userId, UUID merchantId) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, merchantId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + ":"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw MerchantIdentityLinkException.upstream("Could not encrypt merchant identity token", exception);
        }
    }

    public String decrypt(String encrypted, UUID userId, UUID merchantId) {
        if (encrypted == null) {
            return null;
        }
        String[] parts = encrypted.split(":", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new MerchantIdentityLinkException("Unsupported merchant identity token ciphertext");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, merchantId));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new MerchantIdentityLinkException("Could not decrypt merchant identity token", exception);
        }
    }

    private byte[] aad(UUID userId, UUID merchantId) {
        return (userId + ":" + merchantId).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw MerchantIdentityLinkException.upstream("Could not initialize merchant identity token cipher", exception);
        }
    }
}
