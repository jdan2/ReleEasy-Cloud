package com.releasy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.app.AppService;
import com.releasy.application.app.AppUrlPolicy;
import com.releasy.application.auth.AuthService;
import com.releasy.application.aws.AwsConnectionService;
import com.releasy.application.github.GithubConnectionService;
import com.releasy.application.port.AwsConnectionVerifier;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.port.GithubGateway;
import com.releasy.application.port.TokenIssuer;
import com.releasy.application.release.ReleaseLauncher;
import com.releasy.application.release.ReleaseService;
import com.releasy.application.template.TemplateCatalog;
import com.releasy.application.tenant.TenantService;
import com.releasy.application.webhook.GithubWebhookService;
import com.releasy.application.webhook.PushEventFilter;
import com.releasy.application.webhook.WebhookSignatureVerifier;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.domain.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

/**
 * Crea los servicios de la capa de aplicación. Esas clases no llevan anotaciones de Spring, así que se
 * pueden probar con {@code new}. Los repositorios y puertos que reciben vienen de
 * {@link MockAwsConfig} o {@link RealAwsConfig} (según {@code releasy.aws.mode}) y de {@link GithubConfig}
 * (según {@code releasy.github.mode}).
 */
@Configuration
public class ApplicationConfig {

    /** Generado desde el portal con {@code npm run export:catalog}. */
    static final String CATALOG_RESOURCE = "catalog/templates.json";

    @Bean
    TenantService tenantService(TenantRepository tenants, Clock clock) {
        return new TenantService(tenants, clock);
    }

    @Bean
    AuthService authService(UserRepository users, TenantService tenantService, PasswordEncoder passwordEncoder,
                            TokenIssuer tokenIssuer, ReleasyProperties properties, Clock clock) {
        ReleasyProperties.Admin admin = properties.admin();
        return new AuthService(users, tenantService, passwordEncoder, tokenIssuer,
                new AuthService.AdminCredentials(admin.username(), admin.password()), clock);
    }

    @Bean
    AwsConnectionService awsConnectionService(TenantRepository tenants, AwsConnectionVerifier verifier, Clock clock) {
        return new AwsConnectionService(tenants, verifier, clock);
    }

    @Bean
    GithubConnectionService githubConnectionService(TenantRepository tenants, GithubGateway github, Clock clock) {
        return new GithubConnectionService(tenants, github, clock);
    }

    @Bean
    TemplateCatalog templateCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream json = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return TemplateCatalog.load(json, objectMapper);
        }
    }

    @Bean
    AppService appService(AppRepository apps, TenantService tenantService, TemplateCatalog catalog,
                          GithubGateway github, ReleaseLauncher launcher, Clock clock, ReleasyProperties properties) {
        return new AppService(apps, tenantService, catalog, github, launcher,
                new AppUrlPolicy(properties.apps().baseDomain()), clock);
    }

    @Bean
    ReleaseService releaseService(ReleaseRepository releases, AppRepository apps, Clock clock) {
        return new ReleaseService(releases, apps, clock);
    }

    @Bean
    ReleaseLauncher releaseLauncher(ReleaseRepository releases, ReleaseService releaseService,
                                    DeploymentOrchestrator orchestrator, Clock clock) {
        return new ReleaseLauncher(releases, releaseService, orchestrator, clock);
    }

    @Bean
    WebhookSignatureVerifier webhookSignatureVerifier(ReleasyProperties properties) {
        return new WebhookSignatureVerifier(properties.github().webhookSecret());
    }

    @Bean
    GithubWebhookService githubWebhookService(WebhookSignatureVerifier verifier, AppRepository apps,
                                              TenantRepository tenants, ReleaseLauncher launcher,
                                              ObjectMapper objectMapper) {
        return new GithubWebhookService(verifier, new PushEventFilter(), apps, tenants, launcher, objectMapper);
    }
}
