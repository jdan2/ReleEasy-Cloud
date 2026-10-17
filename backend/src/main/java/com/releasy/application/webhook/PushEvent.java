package com.releasy.application.webhook;

/**
 * Campos del evento {@code push} de GitHub que necesita la plataforma.
 *
 * @param ref                p. ej. {@code refs/heads/master}
 * @param after              SHA del commit al que apunta la rama tras el push
 * @param deleted            {@code true} si el push borró la rama
 * @param repositoryFullName {@code owner/repo}
 * @param headCommitMessage  mensaje del último commit (puede estar vacío)
 */
public record PushEvent(String ref, String after, boolean deleted, String repositoryFullName, String headCommitMessage) {
}
