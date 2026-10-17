package com.releasy.application.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida la cabecera {@code X-Hub-Signature-256} de los webhooks de GitHub.
 *
 * <p>GitHub firma el cuerpo <em>exacto</em> de la petición con HMAC-SHA256 y el secreto compartido, y
 * envía {@code sha256=<hex>}. Se recalcula la firma sobre los bytes recibidos (sin volver a serializar
 * el JSON) y se compara con {@link MessageDigest#isEqual}, que tarda lo mismo sea cual sea el primer
 * byte distinto. Así un atacante no puede deducir la firma midiendo tiempos de respuesta.
 */
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final SecretKeySpec key;

    public WebhookSignatureVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("El secreto del webhook de GitHub no puede estar vacío");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public boolean isValid(byte[] payload, String signatureHeader) {
        if (payload == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(payload), provided);
    }

    /** Calcula la cabecera que enviaría GitHub para este cuerpo. Útil en pruebas y para la demo. */
    public String sign(byte[] payload) {
        return PREFIX + HexFormat.of().formatHex(hmac(payload));
    }

    private byte[] hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible en la JVM", e);
        }
    }
}
