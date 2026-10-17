package com.releasy.application.port;

import com.releasy.domain.app.AppPlan;

/**
 * Puerto hacia el motor de despliegue (Step Functions + CodeBuild).
 *
 * <p>Implementaciones: {@code StepFunctionsDeploymentOrchestrator} (modo real), que arranca la
 * máquina de estados, y {@code MockDeploymentOrchestrator} (modo mock), que simula su avance.
 *
 * <p>En modo real, quien actualiza el estado del release en DynamoDB es la propia máquina de estados
 * y el pipeline de CodeBuild, no la API.
 */
public interface DeploymentOrchestrator {

    /**
     * Arranca el despliegue de un release. El nombre de la ejecución es {@code releaseId}, de modo que
     * una misma petición no puede arrancar dos ejecuciones.
     */
    void startDeployment(DeploymentRequest request);

    /** Datos que recibe la máquina de estados como entrada (se serializa a JSON tal cual). */
    record DeploymentRequest(
            String releaseId,
            String tenantId,
            String appId,
            String appName,
            String accountId,
            String tenantRoleArn,
            String repository,
            String branch,
            String commitSha,
            String commitMessage,
            AppPlan plan) {
    }
}
