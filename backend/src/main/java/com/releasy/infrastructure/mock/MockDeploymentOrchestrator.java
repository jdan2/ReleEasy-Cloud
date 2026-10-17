package com.releasy.infrastructure.mock;

import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.release.ReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Simula la máquina de estados (build en CodeBuild seguido de deploy con Terraform):
 *
 * <ul>
 *   <li>tras {@code startDelay}, el release pasa a {@code RUNNING};</li>
 *   <li>tras {@code duration} más, pasa a {@code SUCCEEDED} con una URL de CloudFront ficticia.</li>
 * </ul>
 *
 * <p>Para la demo de errores, si el mensaje del commit contiene {@code [fail]} el release termina en
 * {@code FAILED}.
 */
public class MockDeploymentOrchestrator implements DeploymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MockDeploymentOrchestrator.class);
    static final String FAIL_MARKER = "[fail]";

    private final TaskScheduler scheduler;
    private final ReleaseService releaseService;
    private final Clock clock;
    private final Duration startDelay;
    private final Duration duration;

    public MockDeploymentOrchestrator(TaskScheduler scheduler, ReleaseService releaseService, Clock clock,
                                      Duration startDelay, Duration duration) {
        this.scheduler = scheduler;
        this.releaseService = releaseService;
        this.clock = clock;
        this.startDelay = startDelay;
        this.duration = duration;
    }

    @Override
    public void startDeployment(DeploymentRequest request) {
        Instant runningAt = clock.instant().plus(startDelay);
        scheduler.schedule(() -> safely(request, () -> releaseService.markRunning(request.appId(), request.releaseId())),
                runningAt);
        scheduler.schedule(() -> safely(request, () -> finish(request)), runningAt.plus(duration));
        log.info("[mock] Ejecución simulada {} programada para la aplicación {}", request.releaseId(), request.appId());
    }

    private void finish(DeploymentRequest request) {
        if (request.commitMessage() != null && request.commitMessage().contains(FAIL_MARKER)) {
            releaseService.markFailed(request.appId(), request.releaseId(),
                    "Fallo simulado en el build: el mensaje del commit contiene " + FAIL_MARKER);
        } else {
            releaseService.markSucceeded(request.appId(), request.releaseId(), fakeCloudFrontUrl(request));
        }
    }

    private static void safely(DeploymentRequest request, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            log.warn("[mock] Error avanzando el release {}", request.releaseId(), e);
        }
    }

    /** URL estable por aplicación, con el formato de un dominio de CloudFront. */
    private static String fakeCloudFrontUrl(DeploymentRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((request.tenantId() + "/" + request.appId()).getBytes(StandardCharsets.UTF_8));
            return "https://d" + HexFormat.of().formatHex(hash).substring(0, 13) + ".cloudfront.net";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
