package com.releasy.infrastructure.config;

import com.releasy.application.port.AccountProvisioner;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.release.ReleaseService;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.infrastructure.memory.InMemoryAppRepository;
import com.releasy.infrastructure.memory.InMemoryReleaseRepository;
import com.releasy.infrastructure.memory.InMemoryTenantRepository;
import com.releasy.infrastructure.mock.MockAccountProvisioner;
import com.releasy.infrastructure.mock.MockDeploymentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;

/**
 * Modo {@code mock} (valor por defecto): memoria en lugar de DynamoDB, y Organizations, Step Functions y
 * CodeBuild simulados. No hace ninguna llamada a AWS.
 */
@Configuration
@ConditionalOnProperty(prefix = "releasy.aws", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAwsConfig {

    private static final Logger log = LoggerFactory.getLogger(MockAwsConfig.class);

    MockAwsConfig() {
        log.info("releasy.aws.mode=mock: AWS simulado, datos en memoria (se pierden al reiniciar)");
    }

    @Bean
    TenantRepository tenantRepository() {
        return new InMemoryTenantRepository();
    }

    @Bean
    AppRepository appRepository() {
        return new InMemoryAppRepository();
    }

    @Bean
    ReleaseRepository releaseRepository() {
        return new InMemoryReleaseRepository();
    }

    @Bean
    AccountProvisioner accountProvisioner(Clock clock, ReleasyProperties properties) {
        return new MockAccountProvisioner(clock, properties.aws().mock().accountCreationDelay(),
                properties.aws().accountRoleName());
    }

    @Bean
    DeploymentOrchestrator deploymentOrchestrator(TaskScheduler scheduler, ReleaseService releaseService, Clock clock,
                                                  ReleasyProperties properties) {
        ReleasyProperties.Mock mock = properties.aws().mock();
        return new MockDeploymentOrchestrator(scheduler, releaseService, clock, mock.releaseStartDelay(),
                mock.releaseDuration());
    }
}
