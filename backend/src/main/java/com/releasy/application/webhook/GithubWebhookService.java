package com.releasy.application.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.port.DeploymentOrchestrator.DeploymentRequest;
import com.releasy.application.release.ReleaseService;
import com.releasy.domain.app.App;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.release.ReleaseStatus;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Procesa los webhooks de GitHub:
 *
 * <ol>
 *   <li>valida la firma HMAC-SHA256 sobre el cuerpo recibido;</li>
 *   <li>descarta todo lo que no sea un {@code push} con commit nuevo a una rama;</li>
 *   <li>busca las aplicaciones registradas para ese repositorio y esa rama;</li>
 *   <li>para cada una crea un release {@code PENDING} con el SHA y arranca el despliegue.</li>
 * </ol>
 *
 * <p>Si GitHub reenvía el mismo evento, no se crea un segundo release para el mismo SHA mientras el
 * anterior no haya fallado.
 */
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 200;

    private final WebhookSignatureVerifier verifier;
    private final PushEventFilter filter;
    private final AppRepository apps;
    private final TenantRepository tenants;
    private final ReleaseRepository releases;
    private final ReleaseService releaseService;
    private final DeploymentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GithubWebhookService(WebhookSignatureVerifier verifier, PushEventFilter filter, AppRepository apps,
                                TenantRepository tenants, ReleaseRepository releases, ReleaseService releaseService,
                                DeploymentOrchestrator orchestrator, ObjectMapper objectMapper, Clock clock) {
        this.verifier = verifier;
        this.filter = filter;
        this.apps = apps;
        this.tenants = tenants;
        this.releases = releases;
        this.releaseService = releaseService;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public WebhookResult handle(String eventType, String signatureHeader, byte[] body) {
        if (!verifier.isValid(body, signatureHeader)) {
            throw new InvalidSignatureException();
        }
        if (!filter.isPush(eventType)) {
            return WebhookResult.ignored("Evento '" + eventType + "' ignorado: solo se procesan eventos push");
        }

        PushEvent event = parse(body);
        Optional<String> branch = filter.pushedBranch(event);
        if (branch.isEmpty()) {
            return WebhookResult.ignored("Push ignorado: no actualiza una rama con un commit nuevo (" + event.ref() + ")");
        }

        String repository = event.repositoryFullName().toLowerCase(Locale.ROOT);
        List<App> targets = apps.findByRepository(repository).stream()
                .filter(app -> filter.targets(app, branch.get()))
                .toList();
        if (targets.isEmpty()) {
            return WebhookResult.ignored("Ninguna aplicación registrada para " + repository + " en la rama " + branch.get());
        }

        List<String> releaseIds = new ArrayList<>();
        for (App app : targets) {
            startRelease(app, event).ifPresent(releaseIds::add);
        }
        return releaseIds.isEmpty()
                ? WebhookResult.ignored("No se creó ningún release (commit ya desplegado o cuenta no activa)")
                : WebhookResult.accepted(releaseIds);
    }

    private Optional<String> startRelease(App app, PushEvent event) {
        Tenant tenant = tenants.findById(app.tenantId()).orElse(null);
        if (tenant == null || !tenant.isActive()) {
            log.warn("Push a {} ignorado para la aplicación {}: la cuenta del cliente no está activa", app.repository(), app.id());
            return Optional.empty();
        }
        boolean alreadyReleased = releases.findByAppId(app.id()).stream()
                .anyMatch(r -> r.commitSha().equals(event.after()) && r.status() != ReleaseStatus.FAILED);
        if (alreadyReleased) {
            log.info("El commit {} ya tiene un release para la aplicación {}; se ignora el reenvío", event.after(), app.id());
            return Optional.empty();
        }

        Release release = releases.save(Release.pending(Ids.newReleaseId(clock.instant()), app.id(), tenant.id(),
                event.after(), firstLine(event.headCommitMessage()), clock.instant()));
        try {
            orchestrator.startDeployment(new DeploymentRequest(release.id(), tenant.id(), app.id(), app.name(),
                    tenant.accountId(), tenant.roleArn(), app.repository(), app.branch(), release.commitSha(),
                    release.commitMessage(), app.plan()));
            log.info("Release {} arrancado para la aplicación {} (commit {})", release.id(), app.id(), release.commitSha());
        } catch (RuntimeException e) {
            log.error("No se pudo arrancar el despliegue del release {}", release.id(), e);
            releaseService.markFailed(app.id(), release.id(), "No se pudo arrancar el despliegue: " + e.getMessage());
        }
        return Optional.of(release.id());
    }

    private PushEvent parse(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new InvalidWebhookPayloadException("El cuerpo del webhook no es JSON válido");
        }
        String repository = root.path("repository").path("full_name").asText(null);
        if (repository == null || repository.isBlank()) {
            throw new InvalidWebhookPayloadException("Falta repository.full_name en el evento push");
        }
        return new PushEvent(
                root.path("ref").asText(null),
                root.path("after").asText(null),
                root.path("deleted").asBoolean(false),
                repository,
                root.path("head_commit").path("message").asText(""));
    }

    private static String firstLine(String message) {
        String line = message == null ? "" : message.lines().findFirst().orElse("");
        return line.length() > MAX_COMMIT_MESSAGE_LENGTH ? line.substring(0, MAX_COMMIT_MESSAGE_LENGTH) : line;
    }
}
