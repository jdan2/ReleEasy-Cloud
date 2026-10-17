package com.releasy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.port.AccountProvisioner;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.infrastructure.aws.OrganizationsAccountProvisioner;
import com.releasy.infrastructure.aws.StepFunctionsDeploymentOrchestrator;
import com.releasy.infrastructure.dynamodb.DynamoDbAppRepository;
import com.releasy.infrastructure.dynamodb.DynamoDbReleaseRepository;
import com.releasy.infrastructure.dynamodb.DynamoDbTenantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.sfn.SfnClient;

/**
 * Modo {@code real}: DynamoDB, AWS Organizations y Step Functions con AWS SDK v2.
 *
 * <p>Las credenciales salen de la cadena por defecto del SDK (variables de entorno, perfil local o rol
 * de la tarea ECS). Deben pertenecer a la cuenta de administración de la organización.
 */
@Configuration
@ConditionalOnProperty(prefix = "releasy.aws", name = "mode", havingValue = "real")
public class RealAwsConfig {

    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(ReleasyProperties properties) {
        return DynamoDbClient.builder().region(Region.of(properties.aws().region())).build();
    }

    @Bean
    DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDb) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();
    }

    @Bean
    TenantRepository tenantRepository(DynamoDbEnhancedClient client, ReleasyProperties properties) {
        return new DynamoDbTenantRepository(client, properties.aws().dynamodb().tenantsTable());
    }

    @Bean
    AppRepository appRepository(DynamoDbEnhancedClient client, ReleasyProperties properties) {
        return new DynamoDbAppRepository(client, properties.aws().dynamodb().appsTable());
    }

    @Bean
    ReleaseRepository releaseRepository(DynamoDbEnhancedClient client, ReleasyProperties properties) {
        return new DynamoDbReleaseRepository(client, properties.aws().dynamodb().releasesTable());
    }

    /** Organizations es un servicio global; su endpoint está en us-east-1. */
    @Bean(destroyMethod = "close")
    OrganizationsClient organizationsClient() {
        return OrganizationsClient.builder().region(Region.US_EAST_1).build();
    }

    @Bean
    AccountProvisioner accountProvisioner(OrganizationsClient organizations, ReleasyProperties properties) {
        return new OrganizationsAccountProvisioner(organizations, properties.aws().accountRoleName());
    }

    @Bean(destroyMethod = "close")
    SfnClient sfnClient(ReleasyProperties properties) {
        return SfnClient.builder().region(Region.of(properties.aws().region())).build();
    }

    @Bean
    DeploymentOrchestrator deploymentOrchestrator(SfnClient sfn, ReleasyProperties properties, ObjectMapper objectMapper) {
        String stateMachineArn = properties.aws().stateMachineArn();
        Assert.hasText(stateMachineArn, "En modo real es obligatorio releasy.aws.state-machine-arn (RELEASY_STATE_MACHINE_ARN)");
        return new StepFunctionsDeploymentOrchestrator(sfn, stateMachineArn, objectMapper);
    }
}
