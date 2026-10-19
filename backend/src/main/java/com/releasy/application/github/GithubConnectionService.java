package com.releasy.application.github;

import com.releasy.application.ConflictException;
import com.releasy.application.InvalidRequestException;
import com.releasy.application.NotFoundException;
import com.releasy.application.port.GithubApiException;
import com.releasy.application.port.GithubGateway;
import com.releasy.application.port.GithubGateway.Installation;
import com.releasy.application.port.GithubGateway.Repository;
import com.releasy.application.port.GithubGateway.UserAuthorization;
import com.releasy.domain.tenant.GithubConnection;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Conexión con GitHub a través de la GitHub App de ReleEasy (D-25, D-26).
 *
 * <p>Flujo: el cliente instala la app desde el portal y, en la misma pantalla de GitHub, autoriza a
 * ReleEasy con su usuario (OAuth). GitHub le devuelve al portal con un {@code code} de un solo uso y el
 * {@code installation_id}, y el portal los envía aquí. La API canjea el código, comprueba que la
 * instalación es accesible para ese usuario y la guarda junto con quién la autorizó.
 *
 * <p>Así un cliente no puede vincular la instalación de otra persona solo conociendo su ID.
 */
public class GithubConnectionService {

    private final TenantRepository tenants;
    private final GithubGateway github;
    private final Clock clock;

    public GithubConnectionService(TenantRepository tenants, GithubGateway github, Clock clock) {
        this.tenants = tenants;
        this.github = github;
        this.clock = clock;
    }

    /**
     * Vincula una instalación al cliente.
     *
     * @param code           código OAuth con el que GitHub volvió al portal
     * @param installationId instalación recién hecha; {@code null} si el cliente solo autorizó (la app ya
     *                       estaba instalada), y entonces se usa la primera instalación de su usuario
     */
    public Tenant connect(String tenantId, String code, Long installationId) {
        Tenant tenant = get(tenantId);
        UserAuthorization authorization;
        try {
            authorization = github.authorizeUser(code);
        } catch (GithubApiException e) {
            if (e.status() >= 400 && e.status() < 500) {
                throw new InvalidRequestException("La autorización de GitHub no es válida o ha caducado. Vuelve a conectar.");
            }
            throw e;
        }
        Installation installation = chooseInstallation(authorization, installationId);
        boolean takenByOther = tenants.findAll().stream()
                .anyMatch(t -> !t.id().equals(tenantId) && Objects.equals(t.github().installationId(), installation.id()));
        if (takenByOther) {
            throw new ConflictException("Esta instalación de GitHub ya está vinculada a otro cliente");
        }
        GithubConnection connection = GithubConnection.connected(installation.accountLogin(), installation.id(),
                authorization.userLogin(), clock.instant());
        return tenants.save(tenant.withGithub(connection, clock.instant()));
    }

    private static Installation chooseInstallation(UserAuthorization authorization, Long installationId) {
        List<Installation> installations = authorization.installations();
        if (installationId == null) {
            return installations.stream().findFirst().orElseThrow(() -> new InvalidRequestException(
                    "Tu usuario de GitHub (@" + authorization.userLogin() + ") no tiene ninguna instalación de la app. "
                            + "Instálala primero."));
        }
        return installations.stream()
                .filter(i -> i.id() == installationId)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("Tu usuario de GitHub (@" + authorization.userLogin()
                        + ") no tiene acceso a la instalación " + installationId));
    }

    /**
     * Desvincula la instalación. La app sigue instalada en GitHub hasta que el cliente la desinstale
     * desde la configuración de su cuenta u organización.
     */
    public Tenant disconnect(String tenantId) {
        Tenant tenant = get(tenantId);
        return tenants.save(tenant.withGithub(GithubConnection.notConnected(), clock.instant()));
    }

    /** Repositorios a los que el cliente dio acceso, con sus ramas. */
    public List<Repository> repositories(String tenantId) {
        return github.repositories(requireInstallation(get(tenantId)));
    }

    /** Devuelve el ID de la instalación del cliente o lanza 409 si no ha conectado GitHub. */
    public static long requireInstallation(Tenant tenant) {
        if (!tenant.github().isConnected()) {
            throw new ConflictException("Conecta primero tu cuenta de GitHub");
        }
        return tenant.github().installationId();
    }

    private Tenant get(String tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Cliente", tenantId));
    }
}
