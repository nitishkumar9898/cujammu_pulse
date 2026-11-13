package com.college.events.config;

import com.college.events.repository.InstitutionalIdentityRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

/** Experimental API federation adapter; disabled until an institutional provider is approved. */
@Configuration
@ConditionalOnProperty(name = "app.identity.enabled", havingValue = "true", matchIfMissing = false)
public class OidcResourceServerConfig {
    @Bean
    JwtDecoder institutionalJwtDecoder(@Value("${app.identity.issuer}") String issuer,
                                       @Value("${app.identity.jwks-uri}") String jwksUri,
                                       @Value("${app.identity.audience}") String audience) {
        requireHttps(issuer);
        requireHttps(jwksUri);
        if (audience == null || audience.isBlank()) throw new IllegalArgumentException("Identity audience is required");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            Instant now = Instant.now();
            boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(audience)
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255
                    && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && !jwt.getIssuedAt().isAfter(now.plusSeconds(60))
                    && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofMinutes(15)) <= 0;
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required access-token claims invalid", null));
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), requiredClaims);
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> institutionalAuthenticationConverter(InstitutionalIdentityRepository identities) {
        return new InstitutionalUserConverter(identities);
    }

    static final class InstitutionalUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {
        private final InstitutionalIdentityRepository identities;
        InstitutionalUserConverter(InstitutionalIdentityRepository identities) { this.identities = identities; }
        @Override public AbstractAuthenticationToken convert(Jwt jwt) {
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            if (issuer == null || jwt.getSubject() == null) throw invalidIdentity();
            var user = identities.findByIssuerAndSubjectAndEnabledTrue(issuer, jwt.getSubject())
                    .filter(identity -> identity.isEnabled())
                    // Also compare exactly in Java: a case-insensitive legacy SQL collation must never link identities.
                    .filter(identity -> issuer.equals(identity.getIssuer()) && jwt.getSubject().equals(identity.getSubject()))
                    .map(identity -> identity.getUser()).filter(account -> account != null && account.isEnabled())
                    .orElseThrow(OidcResourceServerConfig::invalidIdentity);
            // Database account roles alone grant authority; token roles/email are deliberately ignored.
            return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        }
    }

    @Bean
    @Order(1)
    SecurityFilterChain institutionalApiChain(HttpSecurity http, JwtDecoder institutionalJwtDecoder,
            Converter<Jwt, AbstractAuthenticationToken> institutionalAuthenticationConverter) throws Exception {
        var resolver = new DefaultBearerTokenResolver();
        http.securityMatcher(OidcResourceServerConfig::isBearerApiRequest)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.deny())
                        .addHeaderWriter(new com.college.events.security.BrowserSecurityHeaders()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/session/csrf").denyAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cujammu/features/*").hasRole("ADMIN")
                        .requestMatchers("/api/v1/cujammu/helpdesk/all").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cujammu/helpdesk/tickets/*/status").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(request -> {
                            if (Collections.list(request.getHeaders("Authorization")).size() != 1) throw invalidIdentity();
                            return resolver.resolve(request);
                        })
                        .jwt(jwt -> jwt.decoder(institutionalJwtDecoder)
                                .jwtAuthenticationConverter(institutionalAuthenticationConverter)));
        return http.build();
    }

    static boolean isBearerApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/") && Collections.list(request.getHeaders("Authorization")).stream()
                .anyMatch(header -> header.stripLeading().regionMatches(true, 0, "Bearer", 0, 6));
    }

    static void requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("Institutional identity URLs must use HTTPS without userinfo, query or fragment");
        }
    }

    private static OAuth2AuthenticationException invalidIdentity() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Institutional identity unavailable", null));
    }
}
