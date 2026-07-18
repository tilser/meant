package com.meant.api.plugin.signing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyOperation;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JsonWebKey(
        String kty,
        String kid,
        String crv,
        String x,
        String y,
        String n,
        String e,
        String alg,
        String use,
        @JsonProperty("key_ops")
        List<String> keyOps
) {
    public JsonWebKey {
        keyOps = keyOps == null ? List.of() : List.copyOf(keyOps);
    }

    public static JsonWebKey from(ECKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return new JsonWebKey(
                key.getKeyType().getValue(),
                key.getKeyID(),
                key.getCurve() == null ? null : key.getCurve().getName(),
                key.getX() == null ? null : key.getX().toString(),
                key.getY() == null ? null : key.getY().toString(),
                null,
                null,
                key.getAlgorithm() == null ? null : key.getAlgorithm().getName(),
                key.getKeyUse() == null ? null : key.getKeyUse().getValue(),
                key.getKeyOperations() == null
                        ? List.of()
                        : key.getKeyOperations().stream().map(KeyOperation::identifier).sorted().toList()
        );
    }
}
