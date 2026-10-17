package com.releasy.application.app;

import com.releasy.application.ConflictException;
import com.releasy.application.NotFoundException;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.app.App;
import com.releasy.domain.app.AppPlan;
import com.releasy.domain.app.AppRepository;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Casos de uso de aplicaciones.
 */
public class AppService {

    private final AppRepository apps;
    private final TenantService tenantService;
    private final AppUrlPolicy urlPolicy;
    private final Clock clock;

    public AppService(AppRepository apps, TenantService tenantService, AppUrlPolicy urlPolicy, Clock clock) {
        this.apps = apps;
        this.tenantService = tenantService;
        this.urlPolicy = urlPolicy;
        this.clock = clock;
    }

    /**
     * Registra una aplicación. Exige que la cuenta AWS del cliente esté activa, porque sin ella no
     * hay dónde desplegar.
     */
    public App register(String tenantId, String name, String repository, String branch, AppPlan plan) {
        Tenant tenant = tenantService.get(tenantId);
        if (!tenant.isActive()) {
            throw new ConflictException("La cuenta AWS del cliente " + tenantId + " no está activa (" + tenant.status() + ")");
        }
        boolean nameTaken = apps.findByTenantId(tenantId).stream().anyMatch(a -> a.name().equals(name));
        if (nameTaken) {
            throw new ConflictException("El cliente ya tiene una aplicación llamada '" + name + "'");
        }
        Instant now = clock.instant();
        App app = new App(Ids.newAppId(), tenantId, name, repository.toLowerCase(Locale.ROOT), branch, plan,
                urlPolicy.provisionalUrl(name, tenant.slug()), now, now);
        return apps.save(app);
    }

    public App get(String id) {
        return apps.findById(id).orElseThrow(() -> new NotFoundException("Aplicación", id));
    }

    public List<App> listByTenant(String tenantId) {
        tenantService.get(tenantId);
        return apps.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(App::createdAt))
                .toList();
    }
}
