package com.releasy.domain.release;

/**
 * Estado de un release y sus transiciones válidas.
 *
 * <pre>
 *   PENDING ──► RUNNING ──► SUCCEEDED
 *      │           │
 *      └───────────┴──────► FAILED
 * </pre>
 *
 * <p>{@code SUCCEEDED} y {@code FAILED} son estados finales. Un release que falla antes de arrancar
 * (p. ej. no se pudo iniciar la ejecución de Step Functions) pasa directamente de {@code PENDING}
 * a {@code FAILED}.
 */
public enum ReleaseStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean canTransitionTo(ReleaseStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == FAILED;
            case RUNNING -> next == SUCCEEDED || next == FAILED;
            case SUCCEEDED, FAILED -> false;
        };
    }
}
