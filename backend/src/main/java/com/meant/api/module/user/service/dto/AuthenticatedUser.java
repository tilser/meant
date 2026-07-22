package com.meant.api.module.user.service.dto;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * The caller's identity as extracted from a validated Supabase JWT.
 *
 * <p>Supabase puts the user id in the {@code sub} claim, the email in {@code email}, and any
 * sign-up or provider profile data under the {@code user_metadata} claim. Names are split into
 * first name / surname so they map onto the {@code users} table; the single "full name" collected
 * on the sign-up screen is split on first token. Standard OpenID Connect name claims are also
 * accepted because providers do not all populate Supabase metadata in the same shape.
 */
public record AuthenticatedUser(
        UUID id,
        String email,
        String firstName,
        String surname
) {

    private static final String USER_METADATA_CLAIM = "user_metadata";
    public static AuthenticatedUser fromJwt(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");

        Map<?, ?> userMetadata = Map.of();
        Object metadata = jwt.getClaim(USER_METADATA_CLAIM);
        if (metadata instanceof Map<?, ?> metadataMap) {
            userMetadata = metadataMap;
        }

        String fullName = firstNonBlank(
                value(userMetadata, "full_name"),
                value(userMetadata, "name"),
                value(userMetadata, "display_name"),
                value(userMetadata, "displayName"),
                claim(jwt, "name"));

        String firstName;
        String surname;
        if (StringUtils.hasText(fullName)) {
            String[] split = splitName(fullName);
            firstName = split[0];
            surname = split[1];
        } else {
            firstName = firstNonBlank(
                    value(userMetadata, "given_name"),
                    value(userMetadata, "first_name"),
                    value(userMetadata, "firstName"),
                    claim(jwt, "given_name"));
            surname = firstNonBlank(
                    value(userMetadata, "family_name"),
                    value(userMetadata, "last_name"),
                    value(userMetadata, "lastName"),
                    claim(jwt, "family_name"));
        }

        return new AuthenticatedUser(
                id,
                email,
                StringUtils.hasText(firstName) ? firstName.trim() : null,
                StringUtils.hasText(surname) ? surname.trim() : null);
    }

    private static String[] splitName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return new String[]{null, null};
        }
        String trimmed = fullName.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) {
            return new String[]{trimmed, null};
        }
        String first = trimmed.substring(0, firstSpace).trim();
        String rest = trimmed.substring(firstSpace + 1).trim();
        return new String[]{first, StringUtils.hasText(rest) ? rest : null};
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String value(Map<?, ?> values, String key) {
        return asString(values.get(key));
    }

    private static String claim(Jwt jwt, String name) {
        return asString(jwt.getClaim(name));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
