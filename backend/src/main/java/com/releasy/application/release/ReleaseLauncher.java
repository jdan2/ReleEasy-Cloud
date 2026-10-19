package com.releasy.application.release;

import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.port.DeploymentOrchestrator.DeploymentRequest;
import com.releasy.domain.app.App;
import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.release.ReleaseStatus;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;

/**
 * Crea un release {@code PENDING} para un commit y arranca su despliegue. Lo usan el alta de una app
 * (último commit de la rama) y el webhook de GitHub (cada push a la rama).
 */
public class ReleaseLauncher {

    private static final Logger log = LoggerFactory.getLogger(ReleaseLauncher.class);
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 200;

    private final ReleaseRepository releases;
    private final ReleaseService releaseService;
    private final DeploymentOrchestrator orchestrator;
    private final Clock clock;

    public ReleaseLauncher(ReleaseRepository releases, ReleaseService releaseService,
                           DeploymentOrchestrator orchestrator, Clock clock) {
        this.releases = releases;
        this.releaseService = releaseService;
        this.orchestrator = orchestrator;
        this.clock = clock;
    }

    /**
     * @return el ID del release creado, o vacío si no se creó: el cliente no tiene la cuenta AWS
     * conectada, o ese commit ya tiene un release que no ha fallado (p. ej. GitHub reenvió el evento)
     */
    public Optional<String> launch(App app, Tenant tenant, String commitSha, String commitMessage) {
        if (!tenant.aws().isConnected()) {
            log.warn("No se despliega {} en la aplicación {}: el cliente no tiene la cuenta AWS conectada", commitSha, app.id());
            return Optional.empty();
        }
        boolean alreadyReleased = releases.findByAppId(app.id()).stream()
                .anyMatch(r -> r.commitSha().equals(commitSha) && r.status() != ReleaseStatus.FAILED);
        if (alreadyReleased) {
            log.info("El commit {} ya tiene un release para la aplicación {}; se ignora", commitSha, app.id());
            return Optional.empty();
        }

        Release release = releases.save(Release.pending(Ids.newReleaseId(clock.instant()), app.id(), tenant.id(),
                commitSha, firstLine(commitMessage), clock.instant()));
        try {
            orchestrator.startDeployment(new DeploymentRequest(release.id(), tenant.id(), app.id(), app.name(),
                    tenant.aws().accountId(), tenant.aws().roleArn(), tenant.aws().externalId(), app.repository(),
                    app.branch(), release.commitSha(), release.commitMessage(), app.templateId(), app.options()));
            log.info("Release {} arrancado para la aplicación {} (commit {})", release.id(), app.id(), release.commitSha());
        } catch (RuntimeException e) {
            log.error("No se pudo arrancar el despliegue del release {}", release.id(), e);
            releaseService.markFailed(app.id(), release.id(), "No se pudo arrancar el despliegue: " + e.getMessage());
        }
        return Optional.of(release.id());
    }

    private static String firstLine(String message) {
        String line = message == null ? "" : message.lines().findFirst().orElse("");
        return line.length() > MAX_COMMIT_MESSAGE_LENGTH ? line.substring(0, MAX_COMMIT_MESSAGE_LENGTH) : line;
    }
}
