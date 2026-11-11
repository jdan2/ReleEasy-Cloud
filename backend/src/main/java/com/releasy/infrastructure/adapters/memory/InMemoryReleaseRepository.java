package com.releasy.infrastructure.adapters.memory;

import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio de releases en memoria (modo mock y pruebas). Imita la clave compuesta de DynamoDB
 * ({@code appId} + {@code releaseId}).
 */
public class InMemoryReleaseRepository implements ReleaseRepository {

    private final Map<String, Release> releases = new ConcurrentHashMap<>();

    @Override
    public Release save(Release release) {
        releases.put(key(release.appId(), release.id()), release);
        return release;
    }

    @Override
    public Optional<Release> findById(String appId, String releaseId) {
        return Optional.ofNullable(releases.get(key(appId, releaseId)));
    }

    @Override
    public List<Release> findByAppId(String appId) {
        return releases.values().stream()
                .filter(r -> r.appId().equals(appId))
                .sorted(Comparator.comparing(Release::id).reversed())
                .toList();
    }

    private static String key(String appId, String releaseId) {
        return appId + "#" + releaseId;
    }
}
