package com.releasy.infrastructure.adapters.mock;

import com.releasy.application.port.GithubGateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * GitHub simulado:
 * <ul>
 *   <li>cualquier código OAuth es válido: lo "autoriza" el usuario {@value #USER_LOGIN}, que tiene acceso
 *       a una instalación en la cuenta {@value #ACCOUNT_LOGIN} cuyo ID se calcula a partir del código
 *       (el mismo código da siempre la misma instalación);</li>
 *   <li>toda instalación da acceso a tres repositorios ficticios;</li>
 *   <li>el último commit de cada rama tiene un SHA fijo, calculado a partir del repositorio y la rama.</li>
 * </ul>
 */
public class MockGithubGateway implements GithubGateway {

    static final String ACCOUNT_LOGIN = "mi-empresa";
    static final String USER_LOGIN = "usuario-demo";

    private static final List<Repository> REPOSITORIES = List.of(
            new Repository(ACCOUNT_LOGIN + "/tienda-online", "Tienda online: Angular + API Spring Boot", true, "main",
                    List.of("main", "develop")),
            new Repository(ACCOUNT_LOGIN + "/web-corporativa", "Web corporativa en Angular", false, "main",
                    List.of("main")),
            new Repository(ACCOUNT_LOGIN + "/api-pedidos", "API de pedidos en Spring Boot", true, "master",
                    List.of("master", "release")));

    @Override
    public UserAuthorization authorizeUser(String code) {
        long installationId = 40_000_000L + Math.floorMod(code.hashCode(), 9_000_000);
        return new UserAuthorization(USER_LOGIN, List.of(new Installation(installationId, ACCOUNT_LOGIN)));
    }

    @Override
    public List<Repository> repositories(long installationId) {
        return REPOSITORIES;
    }

    @Override
    public Optional<Commit> branchHead(long installationId, String repository, String branch) {
        return REPOSITORIES.stream()
                .filter(r -> r.fullName().equalsIgnoreCase(repository) && r.branches().contains(branch))
                .findFirst()
                .map(r -> new Commit(sha1(r.fullName() + "@" + branch), "Último commit de " + branch + " (simulado)"));
    }

    private static String sha1(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
