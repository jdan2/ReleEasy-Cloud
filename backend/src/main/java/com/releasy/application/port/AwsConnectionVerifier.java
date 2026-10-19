package com.releasy.application.port;

/**
 * Comprueba que la plataforma puede asumir el rol {@code ReleEasyDeployRole} de la cuenta del cliente
 * con su External ID (D-18).
 *
 * <p>Implementaciones: {@code StsAwsConnectionVerifier} (modo real: {@code sts:AssumeRole}) y
 * {@code MockAwsConnectionVerifier} (modo mock: solo comprueba el formato del ARN).
 */
public interface AwsConnectionVerifier {

    /**
     * @return el ID de la cuenta AWS del cliente
     * @throws AwsConnectionException si AWS no permite asumir el rol
     */
    String verify(String roleArn, String externalId);

    /** No se pudo asumir el rol. El mensaje se muestra al cliente. Se traduce a HTTP 400. */
    class AwsConnectionException extends RuntimeException {

        public AwsConnectionException(String message) {
            super(message);
        }
    }
}
