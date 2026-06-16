package com.meant.api.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Supplies the {@link JwtDecoder} used to validate Supabase access tokens.
 *
 * <p>When {@code SUPABASE_JWKS_URI} is configured, tokens are validated against Supabase's JWKS
 * endpoint. When it is absent (e.g. local boot without Supabase wired up, or tests that provide
 * their own decoder), a stub decoder is used that rejects every token — secured endpoints then
 * return 401 while public endpoints keep working, so the application always starts.
 */
@Configuration
public class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
        if (StringUtils.hasText(jwkSetUri)) {
            // Supabase signs access tokens with ES256 (asymmetric EC keys). The Nimbus decoder defaults
            // to RS256 only, so the EC algorithms must be declared explicitly or every token is rejected
            // with "Another algorithm expected, or no matching key(s) found".
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                    .jwsAlgorithm(SignatureAlgorithm.ES256)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        }
        return token -> {
            throw new JwtException("JWT validation is not configured (set SUPABASE_JWKS_URI).");
        };
    }
}
