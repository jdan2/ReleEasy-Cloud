package com.releasy.infrastructure.adapters.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.releasy.application.port.GithubApiException;
import com.releasy.application.port.GithubGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente de la API REST de GitHub que actúa como la GitHub App de ReleEasy.
 *
 * <p><b>Autorización del usuario</b> (OAuth, D-26): el {@code code} con el que GitHub vuelve al portal se
 * canjea por un token del usuario ({@code POST /login/oauth/access_token}, con el client ID y el client
 * secret de la app). Con él se consulta quién es ({@code GET /user}) y a qué instalaciones tiene acceso
 * ({@code GET /user/installations}). Ese token no se guarda.
 *
 * <p><b>Acceso a los repositorios</b>, en dos pasos (documentación de GitHub, "Authenticating as a GitHub App"):
 * <ol>
 *   <li>un <b>JWT de la app</b>, firmado con RS256 y su clave privada, válido 10 minutos como máximo;</li>
 *   <li>con él se pide un <b>token de instalación</b> ({@code POST /app/installations/{id}/access_tokens}),
 *       válido 1 hora, que da acceso a los repositorios que el cliente eligió. Se guarda en memoria y se
 *       renueva 5 minutos antes de caducar.</li>
 * </ol>
 *
 * <p>Se usa {@link HttpClient} del JDK. Se piden como máximo 100 repositorios y 100 ramas por
 * repositorio (una página), suficiente para una PYME.
 */
public class GithubAppClient implements GithubGateway {

    private static final Logger log = LoggerFactory.getLogger(GithubAppClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_RENEW_MARGIN = Duration.ofMinutes(5);

    private final String apiUrl;
    private final String oauthUrl;
    private final Credentials credentials;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<Long, InstallationToken> tokens = new ConcurrentHashMap<>();

    /**
     * @param apiUrl   API REST ({@code https://api.github.com})
     * @param oauthUrl web de GitHub, donde se canjean los códigos OAuth ({@code https://github.com})
     */
    public GithubAppClient(String apiUrl, String oauthUrl, Credentials credentials, HttpClient http, ObjectMapper json,
                           Clock clock) {
        this.apiUrl = apiUrl.replaceAll("/+$", "");
        this.oauthUrl = oauthUrl.replaceAll("/+$", "");
        this.credentials = credentials;
        this.http = http;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public UserAuthorization authorizeUser(String code) {
        String userToken = exchangeCode(code);
        String login = send("GET", "/user", userToken).path("login").asText();
        List<Installation> installations = new ArrayList<>();
        for (JsonNode installation : send("GET", "/user/installations?per_page=100", userToken).path("installations")) {
            installations.add(new Installation(installation.path("id").asLong(),
                    installation.path("account").path("login").asText()));
        }
        return new UserAuthorization(login, installations);
    }

    /** GitHub responde 200 también cuando el código no vale, con un campo {@code error}. */
    private String exchangeCode(String code) {
        ObjectNode body = json.createObjectNode()
                .put("client_id", credentials.clientId())
                .put("client_secret", credentials.clientSecret())
                .put("code", code);
        JsonNode response = request("POST", URI.create(oauthUrl + "/login/oauth/access_token"), null, body.toString());
        if (response.hasNonNull("error") || !response.hasNonNull("access_token")) {
            throw new GithubApiException(401, "GitHub rechazó el código de autorización ("
                    + response.path("error").asText("sin token") + ")");
        }
        return response.get("access_token").asText();
    }

    @Override
    public List<Repository> repositories(long installationId) {
        String token = installationToken(installationId);
        JsonNode body = send("GET", "/installation/repositories?per_page=100", token);
        List<Repository> repositories = new ArrayList<>();
        for (JsonNode repo : body.path("repositories")) {
            String fullName = repo.path("full_name").asText();
            List<String> branches = new ArrayList<>();
            send("GET", "/repos/" + fullName + "/branches?per_page=100", token)
                    .forEach(branch -> branches.add(branch.path("name").asText()));
            repositories.add(new Repository(fullName, textOrEmpty(repo.path("description")),
                    repo.path("private").asBoolean(), repo.path("default_branch").asText(), branches));
        }
        return repositories;
    }

    @Override
    public Optional<Commit> branchHead(long installationId, String repository, String branch) {
        try {
            JsonNode body = send("GET", "/repos/" + repository + "/commits/" + encodeRef(branch),
                    installationToken(installationId));
            return Optional.of(new Commit(body.path("sha").asText(), body.path("commit").path("message").asText()));
        } catch (GithubApiException e) {
            if (e.isNotFound() || e.status() == 422) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** JWT de la app. {@code iat} se adelanta 60 s para tolerar diferencias de reloj con GitHub. */
    String appJwt() {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(credentials.appId())
                .issueTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(9))))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(credentials.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el JWT de la GitHub App", e);
        }
        return jwt.serialize();
    }

    private String installationToken(long installationId) {
        InstallationToken cached = tokens.get(installationId);
        if (cached != null && clock.instant().isBefore(cached.expiresAt().minus(TOKEN_RENEW_MARGIN))) {
            return cached.token();
        }
        JsonNode body = send("POST", "/app/installations/" + installationId + "/access_tokens", appJwt());
        InstallationToken fresh = new InstallationToken(body.path("token").asText(),
                Instant.parse(body.path("expires_at").asText()));
        tokens.put(installationId, fresh);
        log.debug("Nuevo token para la instalación {} (caduca {})", installationId, fresh.expiresAt());
        return fresh.token();
    }

    private JsonNode send(String method, String path, String bearer) {
        return request(method, URI.create(apiUrl + path), bearer, null);
    }

    /** Petición a GitHub; {@code bearer} y {@code jsonBody} son opcionales. */
    private JsonNode request(String method, URI uri, String bearer, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", uri.toString().startsWith(oauthUrl) ? "application/json" : "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ReleEasy")
                .method(method, jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new GithubApiException(response.statusCode(),
                        "GitHub respondió " + response.statusCode() + " a " + method + " " + uri.getPath());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new GithubApiException("No se pudo contactar con GitHub", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubApiException("Petición a GitHub interrumpida", e);
        }
    }

    /** Codifica el nombre de la rama, pero deja las barras ({@code feature/x}), que GitHub acepta en la ruta. */
    private static String encodeRef(String ref) {
        return URLEncoder.encode(ref, StandardCharsets.UTF_8).replace("%2F", "/").replace("+", "%20");
    }

    private static String textOrEmpty(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private record InstallationToken(String token, Instant expiresAt) {
    }

    /**
     * Identidad de la GitHub App.
     *
     * @param appId        App ID (emisor del JWT de la app)
     * @param clientId     client ID de la app (OAuth)
     * @param clientSecret client secret de la app (OAuth); secreto, nunca en el repositorio
     * @param privateKey   clave privada con la que se firma el JWT de la app
     */
    public record Credentials(String appId, String clientId, String clientSecret, RSAPrivateKey privateKey) {
    }
}
