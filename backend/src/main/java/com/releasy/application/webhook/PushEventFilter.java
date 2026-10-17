package com.releasy.application.webhook;

import com.releasy.domain.app.App;

import java.util.Optional;

/**
 * Decide qué eventos de GitHub disparan un despliegue.
 *
 * <p>Solo cuenta un {@code push} que actualiza una rama con un commit nuevo. Se descartan los demás
 * eventos ({@code ping}, {@code pull_request}...), los push de tags y los borrados de rama. Hacer merge
 * de un PR en {@code master} genera un {@code push} a {@code master}, que es lo que buscamos.
 */
public class PushEventFilter {

    private static final String BRANCH_REF_PREFIX = "refs/heads/";
    private static final String NULL_SHA = "0000000000000000000000000000000000000000";

    public boolean isPush(String eventType) {
        return "push".equals(eventType);
    }

    /** Rama actualizada por el push, o vacío si el push no debe desplegarse. */
    public Optional<String> pushedBranch(PushEvent event) {
        if (event.deleted() || event.after() == null || event.after().isBlank() || NULL_SHA.equals(event.after())) {
            return Optional.empty();
        }
        if (event.ref() == null || !event.ref().startsWith(BRANCH_REF_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(event.ref().substring(BRANCH_REF_PREFIX.length()));
    }

    /** {@code true} si el push a {@code branch} corresponde a la rama configurada en la aplicación. */
    public boolean targets(App app, String branch) {
        return app.tracksBranch(branch);
    }
}
