package com.releasy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.port.AwsConnectionVerifier;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.release.ReleaseRepository;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.domain.user.UserRepository;
import com.releasy.infrastructure.adapters.aws.StepFunctionsDeploymentOrchestrator;
import com.releasy.infrastructure.adapters.aws.StsAwsConnectionVerifier;
import com.releasy.infrastructure.adapters.dynamodb.DynamoDbAppRepository;
import com.releasy.infrastructure.adapters.dynamodb.DynamoDbReleaseRepository;
import com.releasy.infrastructure.adapters.dynamodb.DynamoDbTenantRepository;
import com.releasy.infrastructure.adapters.dynamodb.DynamoDbUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Modo {@code real}: DynamoDB, STS y Step Functions con AWS SDK v2.
 *
 * <p>Las credenciales salen de la cadena por defecto del SDK (variables de entorno, perfil local o rol
 * de la instancia). Deben pertenecer a la cuenta de la plataforma.
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
    UserRepository userRepository(DynamoDbEnhancedClient client, ReleasyProperties properties) {
        return new DynamoDbUserRepository(client, properties.aws().dynamodb().usersTable());
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

    @Bean(destroyMethod = "close")
    StsClient stsClient(ReleasyProperties properties) {
        return StsClient.builder().region(Region.of(properties.aws().region())).build();
    }

    @Bean
    AwsConnectionVerifier awsConnectionVerifier(StsClient sts) {
        return new StsAwsConnectionVerifier(sts);
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
