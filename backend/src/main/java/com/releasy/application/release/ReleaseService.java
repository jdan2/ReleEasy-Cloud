package com.releasy.application.release;

import com.releasy.application.shared.NotFoundException;
import com.releasy.domain.app.App;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseRepository;

import java.time.Clock;
import java.util.List;

/**
 * Consulta de releases y transiciones de estado.
 *
 * <p>En modo mock, el orquestador simulado usa {@code markRunning/markSucceeded/markFailed}. En modo
 * real esas transiciones las escriben la máquina de estados y CodeBuild directamente en DynamoDB, y la
 * API solo usa {@link #markFailed} si no consigue arrancar la ejecución.
 */
public class ReleaseService {

    private final ReleaseRepository releases;
    private final AppRepository apps;
    private final Clock clock;

    public ReleaseService(ReleaseRepository releases, AppRepository apps, Clock clock) {
        this.releases = releases;
        this.apps = apps;
        this.clock = clock;
    }

    public List<Release> listByApp(String appId) {
        apps.findById(appId).orElseThrow(() -> new NotFoundException("Aplicación", appId));
        return releases.findByAppId(appId);
    }

    public Release markRunning(String appId, String releaseId) {
        return releases.save(get(appId, releaseId).start(clock.instant()));
    }

    /** Marca el release como correcto y fija la URL real como URL pública de la aplicación. */
    public Release markSucceeded(String appId, String releaseId, String url) {
        Release release = releases.save(get(appId, releaseId).succeed(url, clock.instant()));
        apps.findById(appId).ifPresent(app -> saveUrlIfChanged(app, url));
        return release;
    }

    public Release markFailed(String appId, String releaseId, String error) {
        return releases.save(get(appId, releaseId).fail(error, clock.instant()));
    }

    private Release get(String appId, String releaseId) {
        return releases.findById(appId, releaseId).orElseThrow(() -> new NotFoundException("Release", releaseId));
    }

    private void saveUrlIfChanged(App app, String url) {
        if (url != null && !url.equals(app.url())) {
            apps.save(app.withUrl(url, clock.instant()));
        }
    }
}
