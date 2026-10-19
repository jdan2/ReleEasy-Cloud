package com.releasy.application.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.release.ReleaseLauncher;
import com.releasy.domain.app.App;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;

import java.io.IOException;
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
 *   <li>para cada una lanza un release con el SHA ({@link ReleaseLauncher}).</li>
 * </ol>
 *
 * <p>Si GitHub reenvía el mismo evento, no se crea un segundo release para el mismo SHA mientras el
 * anterior no haya fallado.
 */
public class GithubWebhookService {

    private final WebhookSignatureVerifier verifier;
    private final PushEventFilter filter;
    private final AppRepository apps;
    private final TenantRepository tenants;
    private final ReleaseLauncher launcher;
    private final ObjectMapper objectMapper;

    public GithubWebhookService(WebhookSignatureVerifier verifier, PushEventFilter filter, AppRepository apps,
                                TenantRepository tenants, ReleaseLauncher launcher, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.filter = filter;
        this.apps = apps;
        this.tenants = tenants;
        this.launcher = launcher;
        this.objectMapper = objectMapper;
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
                ? WebhookResult.ignored("No se creó ningún release (commit ya desplegado o cuenta AWS sin conectar)")
                : WebhookResult.accepted(releaseIds);
    }

    private Optional<String> startRelease(App app, PushEvent event) {
        Optional<Tenant> tenant = tenants.findById(app.tenantId());
        return tenant.flatMap(t -> launcher.launch(app, t, event.after(), event.headCommitMessage()));
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
}
