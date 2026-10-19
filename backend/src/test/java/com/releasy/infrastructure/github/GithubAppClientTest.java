package com.releasy.infrastructure.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.releasy.application.port.GithubApiException;
import com.releasy.application.port.GithubGateway.Installation;
import com.releasy.application.port.GithubGateway.Repository;
import com.releasy.application.port.GithubGateway.UserAuthorization;
import com.releasy.support.MutableClock;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el cliente contra un servidor HTTP local que imita la API y la web de GitHub. Comprueba el
 * canje del código OAuth, la firma del JWT de la app, el uso del token de instalación y su caché.
 */
class GithubAppClientTest {

    private static final String APP_ID = "123456";
    private static final String CLIENT_ID = "Iv23liPrueba";
    private static final String CLIENT_SECRET = "secreto-oauth";
    private static final String INSTALLATION_TOKEN = "ghs_token_de_prueba";
    private static final String USER_TOKEN = "ghu_token_de_usuario";
    private static final String VALID_CODE = "codigo-valido";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private KeyPair keys;
    private HttpServer server;
    private GithubAppClient client;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::fakeGithub);
        server.start();

        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        GithubAppClient.Credentials credentials =
                new GithubAppClient.Credentials(APP_ID, CLIENT_ID, CLIENT_SECRET, (RSAPrivateKey) keys.getPrivate());
        client = new GithubAppClient(url, url, credentials, HttpClient.newHttpClient(), new ObjectMapper(), clock);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void canjeaElCodigoOauthYDevuelveElUsuarioYSusInstalaciones() {
        UserAuthorization authorization = client.authorizeUser(VALID_CODE);

        assertThat(authorization.userLogin()).isEqualTo("octocat");
        assertThat(authorization.installations()).containsExactly(new Installation(42, "acme"));
    }

    @Test
    void unCodigoNoValidoEsUnError401() {
        // GitHub responde 200 con {"error": ...} cuando el código no vale
        assertThatThrownBy(() -> client.authorizeUser("codigo-caducado"))
                .isInstanceOf(GithubApiException.class)
                .hasMessageContaining("bad_verification_code")
                .satisfies(e -> assertThat(((GithubApiException) e).status()).isEqualTo(401));
    }

    @Test
    void listaRepositoriosConSusRamasYReutilizaElTokenDeInstalacion() {
        List<Repository> repositories = client.repositories(42);

        assertThat(repositories).singleElement().satisfies(repo -> {
            assertThat(repo.fullName()).isEqualTo("acme/tienda");
            assertThat(repo.description()).isEmpty();
            assertThat(repo.isPrivate()).isTrue();
            assertThat(repo.defaultBranch()).isEqualTo("main");
            assertThat(repo.branches()).containsExactly("main", "feature/pagos");
        });

        client.branchHead(42, "acme/tienda", "main");
        assertThat(tokenRequests).hasValue(1);

        // A 4 minutos de caducar (margen de 5), se pide un token nuevo
        clock.advance(Duration.ofMinutes(56));
        client.repositories(42);
        assertThat(tokenRequests).hasValue(2);
    }

    @Test
    void ultimoCommitDeUnaRamaOVacioSiNoExiste() {
        assertThat(client.branchHead(42, "acme/tienda", "main")).hasValueSatisfying(commit -> {
            assertThat(commit.sha()).isEqualTo("abc123");
            assertThat(commit.message()).isEqualTo("Merge pull request #3");
        });
        assertThat(client.branchHead(42, "acme/tienda", "feature/pagos")).isPresent();
        assertThat(client.branchHead(42, "acme/tienda", "no-existe")).isEmpty();
    }

    @Test
    void leeLaClavePrivadaEnPemPkcs1ComoLaEntregaGithubYEnPkcs8() {
        byte[] pkcs8 = keys.getPrivate().getEncoded();
        // En una clave RSA de 2048 bits, la cabecera PKCS#8 ocupa 26 bytes; lo que sigue es la clave PKCS#1.
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);

        RSAPrivateKey fromPkcs1 = GithubAppKeys.parsePrivateKey(pem("RSA PRIVATE KEY", pkcs1));
        RSAPrivateKey fromPkcs8 = GithubAppKeys.parsePrivateKey(pem("PRIVATE KEY", pkcs8));

        assertThat(fromPkcs1.getEncoded()).isEqualTo(pkcs8);
        assertThat(fromPkcs8.getEncoded()).isEqualTo(pkcs8);
        assertThat(GithubAppKeys.derLength(5)).containsExactly(0x05);
        assertThat(GithubAppKeys.derLength(0x04a7)).containsExactly(0x82, 0x04, 0xa7);
    }

    // ---------- GitHub falso ----------

    private void fakeGithub(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        String method = exchange.getRequestMethod();

        if (path.equals("/login/oauth/access_token")) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean valid = body.contains("\"client_id\":\"" + CLIENT_ID + "\"")
                    && body.contains("\"client_secret\":\"" + CLIENT_SECRET + "\"")
                    && body.contains("\"code\":\"" + VALID_CODE + "\"");
            respond(exchange, 200, valid
                    ? "{\"access_token\":\"" + USER_TOKEN + "\",\"token_type\":\"bearer\"}"
                    : "{\"error\":\"bad_verification_code\"}");
            return;
        }
        if (path.startsWith("/user")) {
            if (!("Bearer " + USER_TOKEN).equals(auth)) {
                respond(exchange, 401, "{\"message\":\"Bad credentials\"}");
            } else if (path.equals("/user")) {
                respond(exchange, 200, "{\"login\":\"octocat\"}");
            } else {
                respond(exchange, 200, "{\"total_count\":1,\"installations\":[{\"id\":42,\"account\":{\"login\":\"acme\"}}]}");
            }
            return;
        }
        if (path.startsWith("/app/")) {
            if (!isValidAppJwt(auth)) {
                respond(exchange, 401, "{\"message\":\"A JSON web token could not be decoded\"}");
            } else if (method.equals("POST") && path.equals("/app/installations/42/access_tokens")) {
                tokenRequests.incrementAndGet();
                respond(exchange, 201, "{\"token\":\"%s\",\"expires_at\":\"%s\"}"
                        .formatted(INSTALLATION_TOKEN, clock.instant().plus(Duration.ofHours(1))));
            } else {
                respond(exchange, 404, "{\"message\":\"Not Found\"}");
            }
            return;
        }
        if (!("Bearer " + INSTALLATION_TOKEN).equals(auth)) {
            respond(exchange, 401, "{\"message\":\"Bad credentials\"}");
            return;
        }
        switch (path) {
            case "/installation/repositories" -> respond(exchange, 200, """
                    {"total_count":1,"repositories":[
                      {"full_name":"acme/tienda","description":null,"private":true,"default_branch":"main"}]}""");
            case "/repos/acme/tienda/branches" -> respond(exchange, 200, "[{\"name\":\"main\"},{\"name\":\"feature/pagos\"}]");
            case "/repos/acme/tienda/commits/main", "/repos/acme/tienda/commits/feature/pagos" -> respond(exchange, 200,
                    "{\"sha\":\"abc123\",\"commit\":{\"message\":\"Merge pull request #3\"}}");
            default -> respond(exchange, 404, "{\"message\":\"Not Found\"}");
        }
    }

    /** El JWT debe estar firmado con la clave de la app, tener como emisor el App ID y no estar caducado. */
    private boolean isValidAppJwt(String authorization) {
        try {
            SignedJWT jwt = SignedJWT.parse(authorization.substring("Bearer ".length()));
            return jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic()))
                    && APP_ID.equals(jwt.getJWTClaimsSet().getIssuer())
                    && jwt.getJWTClaimsSet().getExpirationTime().toInstant().isAfter(clock.instant());
        } catch (Exception e) {
            return false;
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END " + type + "-----\n";
    }
}
