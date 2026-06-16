package com.meant.api.module.user.service.dto;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * The caller's identity as extracted from a validated Supabase JWT.
 *
 * <p>Supabase puts the user id in the {@code sub} claim, the email in {@code email}, and any
 * sign-up profile data under the {@code user_metadata} claim (e.g. {@code full_name} or
 * {@code name}). Names are split into first name / surname so they map onto the {@code users}
 * table; the single "full name" collected on the sign-up screen is split on first token.
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

        String firstName = null;
        String surname = null;
        Object metadata = jwt.getClaim(USER_METADATA_CLAIM);
        if (metadata instanceof Map<?, ?> userMetadata) {
            String fullName = firstNonBlank(
                    asString(userMetadata.get("full_name")),
                    asString(userMetadata.get("name")));
            String[] split = splitName(fullName);
            firstName = split[0];
            surname = split[1];
        }

        return new AuthenticatedUser(id, email, firstName, surname);
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

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
