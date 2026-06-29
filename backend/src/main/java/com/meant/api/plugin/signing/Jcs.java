package com.meant.api.plugin.signing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public class Jcs {

    public byte[] canonicalizeToUtf8Bytes(String json) {
        if (json == null) {
            throw new SigningException("JSON payload must not be null");
        }
        return canonicalizeToUtf8Bytes(json.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] canonicalizeToUtf8Bytes(byte[] json) {
        if (json == null) {
            throw new SigningException("JSON payload must not be null");
        }
        try {
            return new JsonCanonicalizer(json).getEncodedUTF8();
        } catch (IOException exception) {
            throw new SigningException("JSON payload could not be canonicalized with RFC 8785 JCS", exception);
        }
    }

    public String canonicalize(String json) {
        return new String(canonicalizeToUtf8Bytes(json), StandardCharsets.UTF_8);
    }
}
