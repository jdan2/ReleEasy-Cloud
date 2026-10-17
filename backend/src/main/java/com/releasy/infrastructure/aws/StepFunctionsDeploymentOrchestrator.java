package com.releasy.infrastructure.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.port.DeploymentOrchestrator;
import software.amazon.awssdk.services.sfn.SfnClient;

/**
 * Arranca la máquina de estados de despliegue ({@code backend/deploy/pipelines/state-machine.asl.json}).
 *
 * <p>La entrada de la ejecución es el {@link DeploymentRequest} serializado a JSON. El nombre de la
 * ejecución es el {@code releaseId}: Step Functions rechaza un nombre repetido, lo que evita
 * ejecuciones duplicadas del mismo release.
 */
public class StepFunctionsDeploymentOrchestrator implements DeploymentOrchestrator {

    private final SfnClient sfn;
    private final String stateMachineArn;
    private final ObjectMapper objectMapper;

    public StepFunctionsDeploymentOrchestrator(SfnClient sfn, String stateMachineArn, ObjectMapper objectMapper) {
        this.sfn = sfn;
        this.stateMachineArn = stateMachineArn;
        this.objectMapper = objectMapper;
    }

    @Override
    public void startDeployment(DeploymentRequest request) {
        sfn.startExecution(b -> b
                .stateMachineArn(stateMachineArn)
                .name(request.releaseId())
                .input(toJson(request)));
    }

    private String toJson(DeploymentRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la entrada de Step Functions", e);
        }
    }
}
