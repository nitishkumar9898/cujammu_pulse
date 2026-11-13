package com.college.events.config;

import com.college.events.model.InstitutionalIdentity;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.InstitutionalIdentityRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstitutionalTokenTest {
    static final String ISSUER = "https://sso.example.invalid/realms/university";
    static final String AUDIENCE = "university-api";

    @Test void rejectsUnsignedAndHmacPublicKeyAlgorithmConfusion() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(OidcResourceServerConfig.validator(ISSUER, AUDIENCE));
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject("subject-123")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        assertThrows(JwtException.class, () -> decoder.decode(new com.nimbusds.jwt.PlainJWT(claims).serialize()));
        var confused = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        confused.sign(new com.nimbusds.jose.crypto.MACSigner(keys.getPublic().getEncoded()));
        assertThrows(JwtException.class, () -> decoder.decode(confused.serialize()));
    }

    @Test void standardDecoderRejectsWrongSignatureIssuerAudienceAndExpiredTokens() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var wrongKeys = generator.generateKeyPair();
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(OidcResourceServerConfig.validator(ISSUER, AUDIENCE));
        var now = Instant.now();
        assertEquals("subject-123", decoder.decode(signed((RSAPrivateKey) keys.getPrivate(), ISSUER, AUDIENCE, now, now.plusSeconds(300))).getSubject());
        assertThrows(JwtException.class, () -> decoder.decode(signed((RSAPrivateKey) wrongKeys.getPrivate(), ISSUER, AUDIENCE, now, now.plusSeconds(300))));
        assertThrows(JwtException.class, () -> decoder.decode(signed((RSAPrivateKey) keys.getPrivate(), "https://other.example.invalid", AUDIENCE, now, now.plusSeconds(300))));
        assertThrows(JwtException.class, () -> decoder.decode(signed((RSAPrivateKey) keys.getPrivate(), ISSUER, "other-api", now, now.plusSeconds(300))));
        assertThrows(JwtException.class, () -> decoder.decode(signed((RSAPrivateKey) keys.getPrivate(), ISSUER, AUDIENCE, now.minusSeconds(600), now.minusSeconds(120))));
    }

    @Test void requiredSubjectExpiryAndBoundedLifetimeCannotBeOmitted() {
        var validator = OidcResourceServerConfig.validator(ISSUER, AUDIENCE);
        var now = Instant.now();
        assertTrue(validator.validate(jwt().claims(claims -> claims.remove("exp")).build()).hasErrors());
        assertTrue(validator.validate(jwt().subject(" ").build()).hasErrors());
        assertTrue(validator.validate(jwt().issuedAt(now.plusSeconds(120)).expiresAt(now.plusSeconds(300)).build()).hasErrors());
        assertTrue(validator.validate(jwt().expiresAt(now.plusSeconds(3600)).build()).hasErrors());
    }

    @Test void mappingRequiresExplicitEnabledLinkAndCurrentAccountIgnoresTokenRoleAndEmail() {
        var identities = mock(InstitutionalIdentityRepository.class);
        var converter = new OidcResourceServerConfig.InstitutionalUserConverter(identities);
        Jwt token = jwt().claim("email", "existing@example.invalid").claim("roles", List.of("ADMIN")).build();
        when(identities.findByIssuerAndSubjectAndEnabledTrue(ISSUER, "subject-123")).thenReturn(Optional.empty());
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(token));
        User user = User.builder().id(9L).name("Synthetic Student").email("student@example.invalid").role(Role.STUDENT).enabled(true).build();
        when(identities.findByIssuerAndSubjectAndEnabledTrue(ISSUER, "subject-123")).thenReturn(Optional.of(
                InstitutionalIdentity.builder().issuer(ISSUER).subject("subject-123").user(user).enabled(true).build()));
        var authentication = converter.convert(token);
        assertSame(user, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_STUDENT")));
        assertFalse(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        assertNull(authentication.getCredentials());
        user.setEnabled(false);
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(token));
        user.setEnabled(true);
        when(identities.findByIssuerAndSubjectAndEnabledTrue(ISSUER, "subject-123")).thenReturn(Optional.of(
                InstitutionalIdentity.builder().issuer(ISSUER).subject("subject-123").user(user).enabled(false).build()));
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(token));
        when(identities.findByIssuerAndSubjectAndEnabledTrue(ISSUER, "subject-123")).thenReturn(Optional.of(
                InstitutionalIdentity.builder().issuer(ISSUER).subject("SUBJECT-123").user(user).enabled(true).build()));
        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(token));
    }

    @Test void adapterIsAbsentUnlessExplicitlyEnabled() {
        new ApplicationContextRunner().withUserConfiguration(OidcResourceServerConfig.class).run(context -> {
            assertFalse(context.containsBean("institutionalApiChain"));
            assertFalse(context.containsBean("institutionalJwtDecoder"));
        });
    }

    @Test void insecureOrCredentialBearingProviderConfigurationIsRejected() {
        for (String uri : List.of("http://sso.example.invalid", "https://user:pass@sso.example.invalid", "https://sso.example.invalid?key=value", "https://sso.example.invalid#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> OidcResourceServerConfig.requireHttps(uri));
        }
        assertDoesNotThrow(() -> OidcResourceServerConfig.requireHttps(ISSUER));
    }

    static Jwt.Builder jwt() {
        return Jwt.withTokenValue("synthetic-token").header("alg", "RS256").issuer(ISSUER).subject("subject-123")
                .audience(List.of(AUDIENCE)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    }
    private static String signed(RSAPrivateKey key, String issuer, String audience, Instant issued, Instant expires) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().issuer(issuer)
                .audience(audience).subject("subject-123").issueTime(Date.from(issued)).expirationTime(Date.from(expires)).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
