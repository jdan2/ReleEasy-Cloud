package com.releasy.domain.shared;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generación de identificadores de las entidades.
 *
 * <p>Los identificadores de release empiezan por la marca de tiempo (UTC, con milisegundos), de modo que su orden
 * lexicográfico coincide con el cronológico. Se usan como clave de ordenación en DynamoDB y como
 * nombre de la ejecución de Step Functions (solo letras, dígitos y guiones).
 */
public final class Ids {

    private static final DateTimeFormatter RELEASE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private Ids() {
    }

    public static String newTenantId() {
        return "t-" + randomSuffix(10);
    }

    public static String newUserId() {
        return "u-" + randomSuffix(10);
    }

    /**
     * External ID de la conexión AWS del cliente. Es un UUID aleatorio: AWS recomienda que sea único y
     * difícil de adivinar para evitar el problema del "suplente confuso".
     */
    public static String newExternalId() {
        return UUID.randomUUID().toString();
    }

    public static String newAppId() {
        return "a-" + randomSuffix(10);
    }

    public static String newReleaseId(Instant now) {
        return "r-" + RELEASE_TIMESTAMP.format(now) + "-" + randomSuffix(6);
    }

    private static String randomSuffix(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
