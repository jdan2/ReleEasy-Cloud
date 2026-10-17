package com.releasy.domain.release;

/**
 * Se intentó mover un release a un estado no permitido desde el actual.
 */
public class IllegalReleaseTransitionException extends RuntimeException {

    public IllegalReleaseTransitionException(String releaseId, ReleaseStatus from, ReleaseStatus to) {
        super("Transición no permitida para el release " + releaseId + ": " + from + " -> " + to);
    }
}
