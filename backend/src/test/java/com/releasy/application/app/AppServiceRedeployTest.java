package com.releasy.application.app;

import com.releasy.application.port.DeploymentOrchestrator;
import com.releasy.application.port.DeploymentOrchestrator.DeploymentRequest;
import com.releasy.application.port.GithubGateway;
import com.releasy.application.release.ReleaseLauncher;
import com.releasy.application.release.ReleaseService;
import com.releasy.application.shared.ConflictException;
import com.releasy.application.shared.InvalidRequestException;
import com.releasy.application.template.TemplateCatalog;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.app.App;
import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseStatus;
import com.releasy.domain.tenant.GithubConnection;
import com.releasy.domain.tenant.Tenant;
import com.releasy.infrastructure.adapters.memory.InMemoryAppRepository;
import com.releasy.infrastructure.adapters.memory.InMemoryReleaseRepository;
import com.releasy.infrastructure.adapters.memory.InMemoryTenantRepository;
import com.releasy.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppServiceRedeployTest {

    private static final String SHA = "9f2c1e0b7a6d5c4b3a291807f6e5d4c3b2a19087";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T10:00:00Z"));
    private final InMemoryTenantRepository tenants = new InMemoryTenantRepository();
    private final InMemoryAppRepository apps = new InMemoryAppRepository();
    private final InMemoryReleaseRepository releases = new InMemoryReleaseRepository();
    private final RecordingOrchestrator orchestrator = new RecordingOrchestrator();
    private final StubGithub github = new StubGithub();
    private ReleaseService releaseService;
    private AppService service;

    @BeforeEach
    void setUp() {
        releaseService = new ReleaseService(releases, apps, clock);
        ReleaseLauncher launcher = new ReleaseLauncher(releases, releaseService, orchestrator, clock);
        service = new AppService(apps, new TenantService(tenants, clock), new TemplateCatalog(List.of()), github,
                launcher, releaseService, new AppUrlPolicy("apps.releasy.local"), clock);

        saveTenant(true, true);
        apps.save(new App("a-1", "t-1", "tienda", "springboot-api", "acme/tienda", "master",
                Map.of("instance_type", "t3.small"), Map.of(), "https://tienda-acme.apps.releasy.local",
                clock.instant(), clock.instant()));
        github.head = Optional.of(new GithubGateway.Commit(SHA, "Arregla el carrito\n\nDetalle"));
    }

    @Test
    void lanzaUnReleasePendingDelUltimoCommitYArrancaElDespliegue() {
        Release release = service.redeploy("a-1");

        assertThat(release.status()).isEqualTo(ReleaseStatus.PENDING);
        assertThat(release.commitSha()).isEqualTo(SHA);
        assertThat(release.commitMessage()).isEqualTo("Arregla el carrito");
        assertThat(orchestrator.requests).hasSize(1);
        assertThat(orchestrator.requests.getFirst().releaseId()).isEqualTo(release.id());
    }

    @Test
    void siElCommitYaTieneUnReleaseEnCursoEsUn409SinDuplicar() {
        service.redeploy("a-1");

        assertThatThrownBy(() -> service.redeploy("a-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya tiene un release en curso o desplegado");
        assertThat(releases.findByAppId("a-1")).hasSize(1);
        assertThat(orchestrator.requests).hasSize(1);
    }

    @Test
    void unCommitDesplegadoConExitoNoSeFuerza() {
        Release first = service.redeploy("a-1");
        releaseService.markRunning("a-1", first.id());
        releaseService.markSucceeded("a-1", first.id(), "https://tienda.example.com");

        assertThatThrownBy(() -> service.redeploy("a-1")).isInstanceOf(ConflictException.class);
        assertThat(releases.findByAppId("a-1")).hasSize(1);
    }

    @Test
    void unaRamaSinCommitsEsUnError400() {
        github.head = Optional.empty();

        assertThatThrownBy(() -> service.redeploy("a-1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no tiene commits");
        assertThat(releases.findByAppId("a-1")).isEmpty();
        assertThat(orchestrator.requests).isEmpty();
    }

    @Test
    void unCommitConReleaseFallidoSePuedeRelanzar() {
        Release failed = service.redeploy("a-1");
        releaseService.markFailed("a-1", failed.id(), "No se pudo arrancar el despliegue");
        clock.advance(Duration.ofMinutes(5));

        Release retry = service.redeploy("a-1");

        assertThat(retry.id()).isNotEqualTo(failed.id());
        assertThat(retry.status()).isEqualTo(ReleaseStatus.PENDING);
        assertThat(releases.findByAppId("a-1")).hasSize(2);
        assertThat(orchestrator.requests).hasSize(2);
    }

    @Test
    void siNoArrancaElDespliegueDevuelveElReleaseFallidoYSePuedeReintentar() {
        orchestrator.failWith = new IllegalStateException("UnknownHostException");

        Release release = service.redeploy("a-1");

        assertThat(release.status()).isEqualTo(ReleaseStatus.FAILED);
        assertThat(release.errorMessage()).contains("UnknownHostException");

        orchestrator.failWith = null;
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.redeploy("a-1").status()).isEqualTo(ReleaseStatus.PENDING);
    }

    @Test
    void sinAwsConectadaEsUnConflicto() {
        saveTenant(false, true);

        assertThatThrownBy(() -> service.redeploy("a-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("AWS");
        assertThat(orchestrator.requests).isEmpty();
    }

    @Test
    void sinGithubConectadoEsUnConflicto() {
        saveTenant(true, false);

        assertThatThrownBy(() -> service.redeploy("a-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("GitHub");
        assertThat(orchestrator.requests).isEmpty();
    }

    private void saveTenant(boolean aws, boolean githubConnected) {
        Tenant tenant = Tenant.register("t-1", "Acme", "acme@example.com", "ext-123", clock.instant());
        if (aws) {
            tenant = tenant.withAws(tenant.aws().connect(
                    "arn:aws:iam::123456789012:role/ReleEasyDeployRole", "123456789012", clock.instant()), clock.instant());
        }
        if (githubConnected) {
            tenant = tenant.withGithub(GithubConnection.connected("acme", 42L, "ana", clock.instant()), clock.instant());
        }
        tenants.save(tenant);
    }

    private static class StubGithub implements GithubGateway {
        Optional<Commit> head = Optional.empty();

        @Override
        public UserAuthorization authorizeUser(String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Repository> repositories(long installationId) {
            return List.of();
        }

        @Override
        public Optional<Commit> branchHead(long installationId, String repository, String branch) {
            return head;
        }
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
