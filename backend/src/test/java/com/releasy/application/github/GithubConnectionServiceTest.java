package com.releasy.application.github;

import com.releasy.application.ConflictException;
import com.releasy.application.InvalidRequestException;
import com.releasy.application.port.GithubApiException;
import com.releasy.application.port.GithubGateway;
import com.releasy.domain.tenant.ConnectionStatus;
import com.releasy.domain.tenant.Tenant;
import com.releasy.infrastructure.memory.InMemoryTenantRepository;
import com.releasy.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubConnectionServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final InMemoryTenantRepository tenants = new InMemoryTenantRepository();
    /** La usuaria "ana" tiene acceso a dos instalaciones: su cuenta personal y su organización. */
    private final GithubGateway github = new FakeGithub(new GithubGateway.UserAuthorization("ana", List.of(
            new GithubGateway.Installation(111, "ana"),
            new GithubGateway.Installation(222, "acme"))));
    private final GithubConnectionService service = new GithubConnectionService(tenants, github, clock);

    @BeforeEach
    void setUp() {
        tenants.save(Tenant.register("t-1", "Acme", "it@acme.es", "ext-1", clock.instant()));
        tenants.save(Tenant.register("t-2", "Otra", "it@otra.es", "ext-2", clock.instant()));
    }

    @Test
    void vinculaLaInstalacionYGuardaQuienLaAutorizo() {
        Tenant tenant = service.connect("t-1", "codigo", 222L);

        assertThat(tenant.github().status()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(tenant.github().installationId()).isEqualTo(222L);
        assertThat(tenant.github().accountLogin()).isEqualTo("acme");
        assertThat(tenant.github().authorizedBy()).isEqualTo("ana");
    }

    @Test
    void sinInstallationIdUsaLaPrimeraInstalacionDelUsuario() {
        assertThat(service.connect("t-1", "codigo", null).github().installationId()).isEqualTo(111L);
    }

    @Test
    void rechazaUnaInstalacionALaQueElUsuarioNoTieneAcceso() {
        assertThatThrownBy(() -> service.connect("t-1", "codigo", 999L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("@ana")
                .hasMessageContaining("999");
    }

    @Test
    void unaInstalacionNoPuedeEstarEnDosClientes() {
        service.connect("t-1", "codigo", 222L);

        assertThatThrownBy(() -> service.connect("t-2", "codigo", 222L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unCodigoRechazadoPorGithubEsUnError400() {
        GithubConnectionService rejecting = new GithubConnectionService(tenants, new FakeGithub(null), clock);

        assertThatThrownBy(() -> rejecting.connect("t-1", "caducado", null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("caducado");
    }

    /** Devuelve siempre la misma autorización; con {@code null}, rechaza el código como GitHub. */
    private record FakeGithub(UserAuthorization authorization) implements GithubGateway {

        @Override
        public UserAuthorization authorizeUser(String code) {
            if (authorization == null) {
                throw new GithubApiException(401, "bad_verification_code");
            }
            return authorization;
        }

        @Override
        public List<Repository> repositories(long installationId) {
            return List.of();
        }

        @Override
        public Optional<Commit> branchHead(long installationId, String repository, String branch) {
            return Optional.empty();
        }
    }
}
