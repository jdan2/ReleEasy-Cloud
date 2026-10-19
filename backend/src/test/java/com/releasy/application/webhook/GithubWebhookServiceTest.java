package com.releasy.application.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.port.DeploymentOrchestrator.DeploymentRequest;
import com.releasy.application.release.ReleaseLauncher;
import com.releasy.application.release.ReleaseService;
import com.releasy.domain.app.App;
import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseStatus;
import com.releasy.domain.tenant.Tenant;
import com.releasy.infrastructure.memory.InMemoryAppRepository;
import com.releasy.infrastructure.memory.InMemoryReleaseRepository;
import com.releasy.infrastructure.memory.InMemoryTenantRepository;
import com.releasy.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubWebhookServiceTest {

    private static final String SECRET = "secreto-de-prueba";
    private static final String SHA = "9f2c1e0b7a6d5c4b3a291807f6e5d4c3b2a19087";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
    private final InMemoryTenantRepository tenants = new InMemoryTenantRepository();
    private final InMemoryAppRepository apps = new InMemoryAppRepository();
    private final InMemoryReleaseRepository releases = new InMemoryReleaseRepository();
    private final RecordingOrchestrator orchestrator = new RecordingOrchestrator();
    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET);
    private GithubWebhookService service;

    @BeforeEach
    void setUp() {
        ReleaseService releaseService = new ReleaseService(releases, apps, clock);
        ReleaseLauncher launcher = new ReleaseLauncher(releases, releaseService, orchestrator, clock);
        service = new GithubWebhookService(verifier, new PushEventFilter(), apps, tenants, launcher, new ObjectMapper());

        Tenant registered = Tenant.register("t-1", "Acme", "acme@example.com", "ext-123", clock.instant());
        Tenant tenant = registered.withAws(registered.aws().connect(
                "arn:aws:iam::123456789012:role/ReleEasyDeployRole", "123456789012", clock.instant()), clock.instant());
        tenants.save(tenant);
        apps.save(new App("a-1", "t-1", "tienda", "springboot-api", "acme/tienda", "master",
                Map.of("instance_type", "t3.small"), Map.of(), "https://tienda-acme.apps.releasy.local",
                clock.instant(), clock.instant()));
    }

    @Test
    void pushAMasterCreaUnReleasePendingYArrancaElDespliegue() {
        WebhookResult result = handle("push", pushPayload("Acme/Tienda", "refs/heads/master", SHA));

        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.ACCEPTED);
        assertThat(result.releaseIds()).hasSize(1);

        Release release = releases.findByAppId("a-1").getFirst();
        assertThat(release.status()).isEqualTo(ReleaseStatus.PENDING);
        assertThat(release.commitSha()).isEqualTo(SHA);
        assertThat(release.commitMessage()).isEqualTo("Merge pull request #7 from acme/feature");

        DeploymentRequest request = orchestrator.requests.getFirst();
        assertThat(request.releaseId()).isEqualTo(release.id());
        assertThat(request.accountId()).isEqualTo("123456789012");
        assertThat(request.tenantRoleArn()).endsWith(":role/ReleEasyDeployRole");
        assertThat(request.externalId()).isEqualTo("ext-123");
        assertThat(request.templateId()).isEqualTo("springboot-api");
        assertThat(request.options()).containsEntry("instance_type", "t3.small");
        assertThat(request.commitSha()).isEqualTo(SHA);
    }

    @Test
    void firmaInvalidaSeRechazaSinTocarNada() {
        byte[] body = pushPayload("acme/tienda", "refs/heads/master", SHA);

        assertThatThrownBy(() -> service.handle("push", "sha256=" + "0".repeat(64), body))
                .isInstanceOf(InvalidSignatureException.class);
        assertThat(releases.findByAppId("a-1")).isEmpty();
        assertThat(orchestrator.requests).isEmpty();
    }

    @Test
    void eventosQueNoSonPushSeIgnoran() {
        WebhookResult result = handle("ping", "{\"zen\":\"Keep it logically awesome.\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.IGNORED);
        assertThat(orchestrator.requests).isEmpty();
    }

    @Test
    void pushAOtraRamaSeIgnora() {
        WebhookResult result = handle("push", pushPayload("acme/tienda", "refs/heads/develop", SHA));

        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.IGNORED);
        assertThat(releases.findByAppId("a-1")).isEmpty();
    }

    @Test
    void pushDeUnRepositorioNoRegistradoSeIgnora() {
        WebhookResult result = handle("push", pushPayload("otra/empresa", "refs/heads/master", SHA));

        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.IGNORED);
        assertThat(orchestrator.requests).isEmpty();
    }

    @Test
    void unReenvioDelMismoCommitNoDuplicaElRelease() {
        byte[] body = pushPayload("acme/tienda", "refs/heads/master", SHA);
        handle("push", body);

        WebhookResult second = handle("push", body);

        assertThat(second.outcome()).isEqualTo(WebhookResult.Outcome.IGNORED);
        assertThat(releases.findByAppId("a-1")).hasSize(1);
    }

    @Test
    void siNoArrancaElDespliegueElReleaseQuedaFailed() {
        orchestrator.failWith = new IllegalStateException("ExecutionLimitExceeded");

        WebhookResult result = handle("push", pushPayload("acme/tienda", "refs/heads/master", SHA));

        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.ACCEPTED);
        Release release = releases.findByAppId("a-1").getFirst();
        assertThat(release.status()).isEqualTo(ReleaseStatus.FAILED);
        assertThat(release.errorMessage()).contains("ExecutionLimitExceeded");
    }

    @Test
    void cuerpoMalFormadoConFirmaValidaEsUnError400() {
        assertThatThrownBy(() -> handle("push", "no-es-json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidWebhookPayloadException.class);
    }

    private WebhookResult handle(String event, byte[] body) {
        return service.handle(event, verifier.sign(body), body);
    }

    private static byte[] pushPayload(String repository, String ref, String sha) {
        String json = """
                {
                  "ref": "%s",
                  "before": "1111111111111111111111111111111111111111",
                  "after": "%s",
                  "deleted": false,
                  "repository": { "full_name": "%s" },
                  "head_commit": { "id": "%s", "message": "Merge pull request #7 from acme/feature\\n\\nDetalle" }
                }
                """.formatted(ref, sha, repository, sha);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static class RecordingOrchestrator implements DeploymentOrchestrator {
        final List<DeploymentRequest> requests = new ArrayList<>();
        RuntimeException failWith;

        @Override
        public void startDeployment(DeploymentRequest request) {
            if (failWith != null) {
                throw failWith;
            }
            requests.add(request);
        }
    }
}
