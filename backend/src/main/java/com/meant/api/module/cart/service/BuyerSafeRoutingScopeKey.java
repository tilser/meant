package com.meant.api.module.cart.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Projects an internal cart routing scope into a stable token safe for buyer and model surfaces. */
public final class BuyerSafeRoutingScopeKey {

    private static final String PREFIX = "cart_scope_";
    private static final Pattern PROJECTED_TOKEN = Pattern.compile("^" + PREFIX + "[0-9a-f]{64}$");

    private BuyerSafeRoutingScopeKey() {
    }

    public static String project(String routingScopeKey) {
        if (routingScopeKey == null || routingScopeKey.isBlank()) {
            return null;
        }
        if (PROJECTED_TOKEN.matcher(routingScopeKey).matches()) {
            return routingScopeKey;
        }
        try {
            String normalizedScope = routingScopeKey.toLowerCase(Locale.ROOT);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedScope.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
