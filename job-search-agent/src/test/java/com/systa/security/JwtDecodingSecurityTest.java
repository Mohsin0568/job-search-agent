package com.systa.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.systa.controller.JobSearchController;
import com.systa.service.JobSearchResultQueryService;
import com.systa.service.JobSearchService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sends real signed tokens through the real JwtDecoder. The other controller tests use jwt(), which
 * skips decoding, so only this test proves that bad tokens are actually rejected. The signing keys
 * come from a local stand-in for Cognito's JWKS endpoint.
 */
@WebMvcTest(controllers = JobSearchController.class, properties = {
        "auth.cognito.client-id=" + JwtDecodingSecurityTest.CLIENT_ID,
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class JwtDecodingSecurityTest {

    static final String CLIENT_ID = "test-client";
    private static final String USER_ID = "cognito-sub-123";
    private static final String KEY_ID = "test-key";

    private static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();
    private static final String ISSUER = "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/test-pool";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobSearchService jobSearchService;

    @MockitoBean
    private JobSearchResultQueryService jobSearchResultQueryService;

    @DynamicPropertySource
    static void issuer(final DynamicPropertyRegistry registry) {
        registry.add("auth.cognito.issuer-uri", () -> ISSUER);
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void validAccessToken_isAccepted_andItsSubjectIsTheUser() throws Exception {
        mockMvc.perform(get("/api/jobs/results").header(HttpHeaders.AUTHORIZATION, bearer(token(claims -> { }))))
                .andExpect(status().isOk());

        verify(jobSearchResultQueryService).findForUser(USER_ID, 0, 50);
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        final Instant anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        assertRejected(token(claims -> claims
                .issueTime(Date.from(anHourAgo.minus(1, ChronoUnit.HOURS)))
                .expirationTime(Date.from(anHourAgo))));
    }

    @Test
    void tokenFromAnotherIssuer_isRejected() throws Exception {
        assertRejected(token(claims -> claims.issuer("https://cognito-idp.eu-west-2.amazonaws.com/another-pool")));
    }

    @Test
    void idToken_isRejected() throws Exception {
        // ID tokens carry the client in "aud" and have no client_id.
        assertRejected(token(claims -> claims
                .claim("token_use", "id")
                .claim("client_id", null)
                .audience(CLIENT_ID)));
    }

    @Test
    void tokenIssuedToAnotherClient_isRejected() throws Exception {
        assertRejected(token(claims -> claims.claim("client_id", "another-client")));
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() throws Exception {
        // Same key id as the published key, so the decoder finds a key and the signature check must fail.
        assertRejected(token(generateKey(), claims -> { }));
    }

    @Test
    void malformedToken_isRejected() throws Exception {
        assertRejected("not-a-jwt");
    }

    private void assertRejected(final String token) throws Exception {
        mockMvc.perform(get("/api/jobs/results").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobSearchResultQueryService);
    }

    private static String bearer(final String token) {
        return "Bearer " + token;
    }

    private static String token(final Consumer<JWTClaimsSet.Builder> customizer) {
        return token(SIGNING_KEY, customizer);
    }

    /** A token shaped like a Cognito access token, valid unless the customizer breaks it. */
    private static String token(final RSAKey key, final Consumer<JWTClaimsSet.Builder> customizer) {
        final Instant now = Instant.now();
        final JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(USER_ID)
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .claim("token_use", "access")
                .claim("client_id", CLIENT_ID);
        customizer.accept(claims);

        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (final JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (final JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwksServer() {
        final byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/test-pool/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            server.start();
            return server;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
