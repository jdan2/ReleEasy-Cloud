package com.releasy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.port.GithubGateway;
import com.releasy.infrastructure.github.GithubAppClient;
import com.releasy.infrastructure.github.GithubAppKeys;
import com.releasy.infrastructure.mock.MockGithubGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/**
 * Elige la implementación de {@link GithubGateway} según {@code releasy.github.mode}. Es independiente
 * de {@code releasy.aws.mode}: se puede usar GitHub real con AWS simulado (D-25).
 */
@Configuration
public class GithubConfig {

    private static final Logger log = LoggerFactory.getLogger(GithubConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "releasy.github", name = "mode", havingValue = "mock", matchIfMissing = true)
    GithubGateway mockGithubGateway() {
        log.info("releasy.github.mode=mock: GitHub simulado (repositorios ficticios)");
        return new MockGithubGateway();
    }

    @Bean
    @ConditionalOnProperty(prefix = "releasy.github", name = "mode", havingValue = "real")
    GithubGateway githubAppClient(ReleasyProperties properties, ObjectMapper objectMapper, Clock clock) {
        ReleasyProperties.Github github = properties.github();
        Assert.hasText(github.appId(), "En modo real es obligatorio releasy.github.app-id (RELEASY_GITHUB_APP_ID)");
        Assert.hasText(github.privateKeyPath(),
                "En modo real es obligatorio releasy.github.private-key-path (RELEASY_GITHUB_PRIVATE_KEY_PATH)");
        Assert.hasText(github.clientId(), "En modo real es obligatorio releasy.github.client-id (RELEASY_GITHUB_CLIENT_ID)");
        Assert.hasText(github.clientSecret(),
                "En modo real es obligatorio releasy.github.client-secret (RELEASY_GITHUB_CLIENT_SECRET)");
        String pem;
        try {
            pem = Files.readString(Path.of(github.privateKeyPath().replaceFirst("^~", System.getProperty("user.home"))));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la clave privada de la GitHub App: " + github.privateKeyPath(), e);
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        log.info("releasy.github.mode=real: GitHub App {} contra {}", github.appId(), github.apiUrl());
        GithubAppClient.Credentials credentials = new GithubAppClient.Credentials(github.appId(), github.clientId(),
                github.clientSecret(), GithubAppKeys.parsePrivateKey(pem));
        return new GithubAppClient(github.apiUrl(), github.oauthUrl(), credentials, http, objectMapper, clock);
    }
}
