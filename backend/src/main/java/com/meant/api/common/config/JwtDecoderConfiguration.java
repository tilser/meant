package com.meant.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Supplies the {@link JwtDecoder} used to validate Supabase access tokens.
 *
 * <p>When {@code SUPABASE_JWKS_URI} is configured, tokens are validated against Supabase's JWKS
 * endpoint. When it is absent (e.g. local boot without Supabase wired up, or tests that provide
 * their own decoder), a stub decoder is used that rejects every token — secured endpoints then
 * return 401 while public endpoints keep working, so the application always starts.
 */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
        if (!StringUtils.hasText(jwkSetUri)) {
            return token -> {
                throw new JwtException("JWT validation is not configured (set SUPABASE_JWKS_URI).");
            };
        }
        // Supabase signs access tokens with ES256 (asymmetric EC keys). The Nimbus decoder defaults to
        // RS256 only, so the EC algorithms must be declared explicitly or every token is rejected with
        // "Another algorithm expected, or no matching key(s) found".
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(tokenValidator(issuerUri));
        return decoder;
    }

    /**
     * Validates timestamps (exp/nbf) and, when an issuer is configured, that the {@code iss} claim
     * matches our Supabase project — closing off tokens minted by any other issuer whose key the JWKS
     * happens to serve.
     */
    private OAuth2TokenValidator<Jwt> tokenValidator(String issuerUri) {
        if (StringUtils.hasText(issuerUri)) {
            return JwtValidators.createDefaultWithIssuer(issuerUri);
        }
        return JwtValidators.createDefault();
    }
}
