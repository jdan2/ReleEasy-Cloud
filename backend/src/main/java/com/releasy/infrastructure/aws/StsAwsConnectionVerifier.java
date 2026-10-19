package com.releasy.infrastructure.aws;

import com.releasy.application.port.AwsConnectionVerifier;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.StsException;

/**
 * Verifica la conexión asumiendo el rol del cliente con su External ID ({@code sts:AssumeRole}). Las
 * credenciales temporales que devuelve AWS no se usan ni se guardan: solo importa que la llamada funcione.
 */
public class StsAwsConnectionVerifier implements AwsConnectionVerifier {

    private static final String SESSION_NAME = "releasy-verify";
    /** Mínimo que admite AWS (15 minutos). */
    private static final int SESSION_SECONDS = 900;

    private final StsClient sts;

    public StsAwsConnectionVerifier(StsClient sts) {
        this.sts = sts;
    }

    @Override
    public String verify(String roleArn, String externalId) {
        try {
            AssumeRoleResponse response = sts.assumeRole(r -> r
                    .roleArn(roleArn)
                    .externalId(externalId)
                    .roleSessionName(SESSION_NAME)
                    .durationSeconds(SESSION_SECONDS));
            // arn:aws:sts::<cuenta>:assumed-role/<rol>/<sesión>
            return response.assumedRoleUser().arn().split(":")[4];
        } catch (StsException e) {
            String code = e.awsErrorDetails() == null ? "error desconocido" : e.awsErrorDetails().errorCode();
            throw new AwsConnectionException("AWS no permitió asumir el rol (" + code + "). Comprueba que el stack "
                    + "de CloudFormation terminó y que la política de confianza exige tu External ID.");
        }
    }
}
