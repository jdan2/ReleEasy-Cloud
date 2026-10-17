package com.releasy.application.webhook;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("It's a Secret to Everybody");

    /**
     * Vector de prueba publicado por GitHub en "Validating webhook deliveries":
     * secreto "It's a Secret to Everybody" y cuerpo "Hello, World!".
     */
    @Test
    void aceptaElVectorDePruebaOficialDeGithub() {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

        assertThat(verifier.isValid(payload, signature)).isTrue();
        assertThat(verifier.sign(payload)).isEqualTo(signature);
    }

    @Test
    void rechazaUnCuerpoModificado() {
        String signature = verifier.sign(bytes("{\"ref\":\"refs/heads/master\"}"));

        assertThat(verifier.isValid(bytes("{\"ref\":\"refs/heads/evil\"}"), signature)).isFalse();
    }

    @Test
    void rechazaUnaFirmaHechaConOtroSecreto() {
        byte[] payload = bytes("{}");
        String otherSignature = new WebhookSignatureVerifier("otro-secreto").sign(payload);

        assertThat(verifier.isValid(payload, otherSignature)).isFalse();
    }

    @Test
    void rechazaCabeceraAusenteOConFormatoIncorrecto() {
        byte[] payload = bytes("{}");
        String validHex = verifier.sign(payload).substring("sha256=".length());

        assertThat(verifier.isValid(payload, null)).isFalse();
        assertThat(verifier.isValid(payload, "")).isFalse();
        assertThat(verifier.isValid(payload, validHex)).as("sin prefijo sha256=").isFalse();
        assertThat(verifier.isValid(payload, "sha1=" + validHex)).as("algoritmo antiguo").isFalse();
        assertThat(verifier.isValid(payload, "sha256=no-es-hex")).isFalse();
        assertThat(verifier.isValid(payload, "sha256=" + validHex.substring(0, 10))).as("truncada").isFalse();
    }

    @Test
    void noAdmiteSecretoVacio() {
        assertThatThrownBy(() -> new WebhookSignatureVerifier(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
