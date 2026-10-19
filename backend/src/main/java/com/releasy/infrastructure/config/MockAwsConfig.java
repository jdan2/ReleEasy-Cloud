package com.releasy.infrastructure.config;

import com.releasy.application.auth.AuthService;
import com.releasy.application.port.AwsConnectionVerifier;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.release.ReleaseService;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.domain.user.UserRepository;
import com.releasy.infrastructure.memory.InMemoryAppRepository;
import com.releasy.infrastructure.memory.InMemoryReleaseRepository;
import com.releasy.infrastructure.memory.InMemoryTenantRepository;
import com.releasy.infrastructure.memory.InMemoryUserRepository;
import com.releasy.infrastructure.mock.MockAwsConnectionVerifier;
import com.releasy.infrastructure.mock.MockDeploymentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;

/**
 * Modo {@code mock} (valor por defecto): memoria en lugar de DynamoDB, y STS, Step Functions y CodeBuild
 * simulados. No hace ninguna llamada a AWS.
 */
@Configuration
@ConditionalOnProperty(prefix = "releasy.aws", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAwsConfig {

    private static final Logger log = LoggerFactory.getLogger(MockAwsConfig.class);
    static final String DEMO_EMAIL = "demo@ferreteria-lopez.es";
    static final String DEMO_PASSWORD = "demo1234";

    MockAwsConfig() {
        log.info("releasy.aws.mode=mock: AWS simulado, datos en memoria (se pierden al reiniciar)");
    }

    @Bean
    UserRepository userRepository() {
        return new InMemoryUserRepository();
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
    AwsConnectionVerifier awsConnectionVerifier() {
        return new MockAwsConnectionVerifier();
    }

    /**
     * Con {@code releasy.demo.seed-user=true} (perfil {@code mock-fast}) crea un cliente de demostración
     * al arrancar, para no tener que registrarse después de cada reinicio.
     */
    @Bean
    @ConditionalOnProperty(prefix = "releasy.demo", name = "seed-user", havingValue = "true")
    ApplicationRunner seedDemoUser(AuthService authService) {
        return args -> {
            authService.register("Ferretería López", DEMO_EMAIL, DEMO_PASSWORD);
            log.info("Cliente de demostración creado: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
        };
    }

    @Bean
    DeploymentOrchestrator deploymentOrchestrator(TaskScheduler scheduler, ReleaseService releaseService, Clock clock,
                                                  ReleasyProperties properties) {
        ReleasyProperties.Mock mock = properties.aws().mock();
        return new MockDeploymentOrchestrator(scheduler, releaseService, clock, mock.releaseStartDelay(),
                mock.releaseDuration());
    }
}
