package com.meant.api.plugin.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JcsTest {

    private final Jcs jcs = new Jcs();

    @Test
    void canonicalizesRfc8785JsonVector() {
        String canonical = jcs.canonicalize("""
                {
                  "numbers": [333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001],
                  "string": "€$%s\\nA'B\\"\\\\\\"/",
                  "literals": [null, true, false]
                }
                """.formatted("\\u000f"));

        assertThat(canonical).isEqualTo(
                "{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],"
                        + "\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\"/\"}"
        );
    }

    @Test
    void canonicalBytesAreUtf8BytesUsedOnWire() {
        byte[] canonical = jcs.canonicalizeToUtf8Bytes("""
                {
                  "z": 0,
                  "a": {"b": 2, "a": 1},
                  "array": [{"y": 2, "x": 1}]
                }
                """);

        assertThat(new String(canonical, StandardCharsets.UTF_8))
                .isEqualTo("{\"a\":{\"a\":1,\"b\":2},\"array\":[{\"x\":1,\"y\":2}],\"z\":0}");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> jcs.canonicalize("{\"a\":}"))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("canonicalized");
    }
}
