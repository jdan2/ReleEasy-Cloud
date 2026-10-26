package com.releasy.infrastructure.adapters.mock;

import com.releasy.application.port.AwsConnectionVerifier;

/**
 * Simula {@code sts:AssumeRole}: acepta cualquier ARN bien formado (el formato ya lo comprueba
 * {@code AwsConnectionService}) y devuelve la cuenta que aparece en él.
 *
 * <p>Para la demo de errores, un ARN que contenga {@code fail} se rechaza como si AWS devolviera
 * {@code AccessDenied}, igual que en el modo demo del portal.
 */
public class MockAwsConnectionVerifier implements AwsConnectionVerifier {

    static final String FAIL_MARKER = "fail";

    @Override
    public String verify(String roleArn, String externalId) {
        if (roleArn.toLowerCase().contains(FAIL_MARKER)) {
            throw new AwsConnectionException("AWS devolvió AccessDenied al asumir el rol (simulado). Comprueba que "
                    + "el stack de CloudFormation terminó y que la política de confianza exige tu External ID.");
        }
        return roleArn.split(":")[4];
    }
}
