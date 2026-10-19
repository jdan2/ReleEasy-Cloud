package com.releasy.application.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.releasy.application.ConflictException;
import com.releasy.application.InvalidRequestException;
import com.releasy.application.NotFoundException;
import com.releasy.application.github.GithubConnectionService;
import com.releasy.application.port.GithubGateway;
import com.releasy.application.port.GithubGateway.Commit;
import com.releasy.application.port.GithubGateway.Repository;
import com.releasy.application.release.ReleaseLauncher;
import com.releasy.application.template.TemplateCatalog;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.app.App;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Casos de uso de aplicaciones.
 */
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);
    /** Mismo formato que valida el portal: se usa en nombres de recursos AWS. */
    private static final Pattern APP_NAME = Pattern.compile("^[a-z][a-z0-9-]{2,19}$");

    private final AppRepository apps;
    private final TenantService tenantService;
    private final TemplateCatalog catalog;
    private final GithubGateway github;
    private final ReleaseLauncher launcher;
    private final AppUrlPolicy urlPolicy;
    private final Clock clock;

    public AppService(AppRepository apps, TenantService tenantService, TemplateCatalog catalog, GithubGateway github,
                      ReleaseLauncher launcher, AppUrlPolicy urlPolicy, Clock clock) {
        this.apps = apps;
        this.tenantService = tenantService;
        this.catalog = catalog;
        this.github = github;
        this.launcher = launcher;
        this.urlPolicy = urlPolicy;
        this.clock = clock;
    }

    /**
     * Crea una aplicación y lanza el despliegue del último commit de la rama. Después, cada push a la
     * rama lanza otro (webhook).
     *
     * <p>Exige la cuenta AWS y GitHub conectadas, una plantilla del catálogo con opciones válidas, y que
     * el repositorio y la rama estén en la instalación de GitHub del cliente.
     */
    public App create(String tenantId, NewApp request) {
        Tenant tenant = tenantService.get(tenantId);
        if (!tenant.aws().isConnected()) {
            throw new ConflictException("Conecta primero tu cuenta de AWS");
        }
        long installationId = GithubConnectionService.requireInstallation(tenant);

        JsonNode template = catalog.find(request.templateId())
                .orElseThrow(() -> new InvalidRequestException("Plantilla desconocida: " + request.templateId()));
        String name = request.name() == null ? "" : request.name();
        if (!APP_NAME.matcher(name).matches()) {
            throw new InvalidRequestException("Nombre: 3-20 caracteres en minúsculas, dígitos o guiones; empieza por letra");
        }
        if (apps.findByTenantId(tenantId).stream().anyMatch(a -> a.name().equals(name))) {
            throw new ConflictException("Ya tienes una aplicación llamada '" + name + "'");
        }
        Repository repository = github.repositories(installationId).stream()
                .filter(r -> r.fullName().equalsIgnoreCase(request.repository()))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(
                        "El repositorio " + request.repository() + " no está en tu instalación de GitHub"));
        if (!repository.branches().contains(request.branch())) {
            throw new InvalidRequestException("La rama " + request.branch() + " no existe en " + repository.fullName());
        }
        Map<String, Object> options = catalog.knownOptions(template, request.options());
        List<String> errors = catalog.validateOptions(template, options);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" ", errors));
        }

        Instant now = clock.instant();
        App app = apps.save(new App(Ids.newAppId(), tenantId, name, request.templateId(),
                repository.fullName().toLowerCase(Locale.ROOT), request.branch(), options,
                catalog.knownUsage(template, request.usage()), urlPolicy.provisionalUrl(name, tenant.slug()), now, now));
        deployLatestCommit(app, tenant, installationId, repository.fullName());
        return app;
    }

    /** Primer despliegue. Si GitHub falla, la app queda creada y se desplegará con el siguiente push. */
    private void deployLatestCommit(App app, Tenant tenant, long installationId, String repository) {
        try {
            Optional<Commit> head = github.branchHead(installationId, repository, app.branch());
            head.ifPresent(commit -> launcher.launch(app, tenant, commit.sha(), commit.message()));
        } catch (RuntimeException e) {
            log.warn("No se pudo lanzar el primer despliegue de la aplicación {}", app.id(), e);
        }
    }

    public App get(String id) {
        return apps.findById(id).orElseThrow(() -> new NotFoundException("Aplicación", id));
    }

    public List<App> listByTenant(String tenantId) {
        tenantService.get(tenantId);
        return apps.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(App::createdAt))
                .toList();
    }

    /** Datos que envía el cliente para crear una app ({@code POST /api/me/apps}). */
    public record NewApp(String name, String templateId, String repository, String branch,
                         Map<String, Object> options, Map<String, Object> usage) {
    }
}
