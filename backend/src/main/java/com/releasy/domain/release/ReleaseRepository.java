package com.releasy.domain.release;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de releases. Implementaciones: memoria (modo mock) y DynamoDB (tabla {@code releases},
 * clave de partición {@code appId} y de ordenación {@code releaseId}).
 */
public interface ReleaseRepository {

    Release save(Release release);

    Optional<Release> findById(String appId, String releaseId);

    /** Releases de una aplicación, del más reciente al más antiguo. */
    List<Release> findByAppId(String appId);
}
