package com.releasy.application.webhook;

import java.util.List;

/**
 * Resultado de procesar un webhook: aceptado (se crearon releases) o ignorado (con el motivo).
 */
public record WebhookResult(Outcome outcome, String message, List<String> releaseIds) {

    public enum Outcome { ACCEPTED, IGNORED }

    public static WebhookResult accepted(List<String> releaseIds) {
        return new WebhookResult(Outcome.ACCEPTED, "Releases creados: " + releaseIds.size(), List.copyOf(releaseIds));
    }

    public static WebhookResult ignored(String reason) {
        return new WebhookResult(Outcome.IGNORED, reason, List.of());
    }
}
