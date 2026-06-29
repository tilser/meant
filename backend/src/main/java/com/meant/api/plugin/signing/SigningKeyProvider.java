package com.meant.api.plugin.signing;

import java.util.List;
import java.util.Optional;

public interface SigningKeyProvider {

    SigningKey activePrivateKey(SigningKeyPurpose purpose);

    Optional<SigningKey> key(String kid, SigningKeyPurpose purpose);

    List<PublicSigningKey> publicKeys();

    static SigningKeyProvider empty() {
        return new SigningKeyProvider() {
            @Override
            public SigningKey activePrivateKey(SigningKeyPurpose purpose) {
                throw new SigningException("No active signing key is configured for purpose " + purpose.value());
            }

            @Override
            public Optional<SigningKey> key(String kid, SigningKeyPurpose purpose) {
                return Optional.empty();
            }

            @Override
            public List<PublicSigningKey> publicKeys() {
                return List.of();
            }
        };
    }
}
