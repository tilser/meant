package com.meant.api.plugin.signing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
}
