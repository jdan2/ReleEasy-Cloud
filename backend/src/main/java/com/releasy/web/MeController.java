package com.releasy.web;

import com.releasy.application.app.AppService;
import com.releasy.application.aws.AwsConnectionService;
import com.releasy.application.github.GithubConnectionService;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.tenant.Tenant;
import com.releasy.infrastructure.config.ReleasyProperties;
import com.releasy.web.dto.AppResponse;
import com.releasy.web.dto.ConnectAwsRequest;
import com.releasy.web.dto.ConnectGithubRequest;
import com.releasy.web.dto.CreateAppRequest;
import com.releasy.web.dto.GithubRepositoryResponse;
import com.releasy.web.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Rutas del cliente conectado ({@code /api/me/*}). El cliente sale siempre del token, nunca de la URL,
 * así que un cliente no puede consultar ni modificar los datos de otro.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final TenantService tenantService;
    private final AwsConnectionService awsConnectionService;
    private final GithubConnectionService githubConnectionService;
    private final AppService appService;
    private final String platformAccountId;

    public MeController(TenantService tenantService, AwsConnectionService awsConnectionService,
                        GithubConnectionService githubConnectionService, AppService appService,
                        ReleasyProperties properties) {
        this.tenantService = tenantService;
        this.awsConnectionService = awsConnectionService;
        this.githubConnectionService = githubConnectionService;
        this.appService = appService;
        this.platformAccountId = properties.aws().platformAccountId();
    }

    @GetMapping("/tenant")
    TenantResponse tenant(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(tenantService.get(CurrentUser.tenantId(jwt)));
    }

    // ---------- Cuenta AWS ----------

    /** Verifica que la plataforma puede asumir el rol con el External ID del cliente y lo guarda. */
    @PostMapping("/aws-connection")
    TenantResponse connectAws(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConnectAwsRequest request) {
        return toResponse(awsConnectionService.connect(CurrentUser.tenantId(jwt), request.roleArn()));
    }

    @DeleteMapping("/aws-connection")
    TenantResponse disconnectAws(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(awsConnectionService.disconnect(CurrentUser.tenantId(jwt)));
    }

    // ---------- GitHub ----------

    /** Vincula la instalación de la GitHub App con lo que GitHub entregó al portal (código OAuth e instalación). */
    @PostMapping("/github-connection")
    TenantResponse connectGithub(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConnectGithubRequest request) {
        return toResponse(githubConnectionService.connect(CurrentUser.tenantId(jwt), request.code(),
                request.installationId()));
    }

    @DeleteMapping("/github-connection")
    TenantResponse disconnectGithub(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(githubConnectionService.disconnect(CurrentUser.tenantId(jwt)));
    }

    /** Repositorios de la instalación, con sus ramas. 409 si el cliente no ha conectado GitHub. */
    @GetMapping("/github/repositories")
    List<GithubRepositoryResponse> repositories(@AuthenticationPrincipal Jwt jwt) {
        return githubConnectionService.repositories(CurrentUser.tenantId(jwt)).stream()
                .map(GithubRepositoryResponse::from)
                .toList();
    }

    // ---------- Aplicaciones ----------

    @GetMapping("/apps")
    List<AppResponse> apps(@AuthenticationPrincipal Jwt jwt) {
        return appService.listByTenant(CurrentUser.tenantId(jwt)).stream().map(AppResponse::from).toList();
    }

    /** Crea la app y lanza el despliegue del último commit de la rama. */
    @PostMapping("/apps")
    ResponseEntity<AppResponse> createApp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAppRequest request) {
        AppResponse app = AppResponse.from(appService.create(CurrentUser.tenantId(jwt), request.toNewApp()));
        return ResponseEntity.created(URI.create("/api/apps/" + app.id())).body(app);
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.from(tenant, platformAccountId, appService.listByTenant(tenant.id()).size());
    }
}
