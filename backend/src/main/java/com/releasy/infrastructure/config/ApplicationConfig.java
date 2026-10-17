package com.releasy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.app.AppService;
import com.releasy.application.app.AppUrlPolicy;
import com.releasy.application.port.AccountProvisioner;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.release.ReleaseService;
import com.releasy.application.tenant.AccountCreationPoller;
import com.releasy.application.tenant.TenantService;
import com.releasy.application.webhook.GithubWebhookService;
import com.releasy.application.webhook.PushEventFilter;
import com.releasy.application.webhook.WebhookSignatureVerifier;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.tenant.TenantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Crea los servicios de la capa de aplicación. Esas clases no llevan anotaciones de Spring, así que se
 * pueden probar con {@code new}. Los repositorios y puertos que reciben vienen de
 * {@link MockAwsConfig} o {@link RealAwsConfig}, según {@code releasy.aws.mode}.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    TenantService tenantService(TenantRepository tenants, AccountProvisioner provisioner, Clock clock) {
        return new TenantService(tenants, provisioner, clock);
    }

    @Bean
    AccountCreationPoller accountCreationPoller(TenantRepository tenants, AccountProvisioner provisioner, Clock clock,
                                                ReleasyProperties properties) {
        return new AccountCreationPoller(tenants, provisioner, clock, properties.aws().accountCreationTimeout());
    }

    @Bean
    AppService appService(AppRepository apps, TenantService tenantService, Clock clock, ReleasyProperties properties) {
        return new AppService(apps, tenantService, new AppUrlPolicy(properties.apps().baseDomain()), clock);
    }

    @Bean
    ReleaseService releaseService(ReleaseRepository releases, AppRepository apps, Clock clock) {
        return new ReleaseService(releases, apps, clock);
    }

    @Bean
    WebhookSignatureVerifier webhookSignatureVerifier(ReleasyProperties properties) {
        return new WebhookSignatureVerifier(properties.github().webhookSecret());
    }

    @Bean
    GithubWebhookService githubWebhookService(WebhookSignatureVerifier verifier, AppRepository apps,
                                              TenantRepository tenants, ReleaseRepository releases,
                                              ReleaseService releaseService, DeploymentOrchestrator orchestrator,
                                              ObjectMapper objectMapper, Clock clock) {
        return new GithubWebhookService(verifier, new PushEventFilter(), apps, tenants, releases, releaseService,
                orchestrator, objectMapper, clock);
    }
}
