package com.releasy.application.tenant;

import com.releasy.application.shared.NotFoundException;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

/**
 * Casos de uso de clientes. El alta la hace el propio cliente al registrarse (ver
 * {@link com.releasy.application.auth.AuthService}).
 */
public class TenantService {

    private final TenantRepository tenants;
    private final Clock clock;

    public TenantService(TenantRepository tenants, Clock clock) {
        this.tenants = tenants;
        this.clock = clock;
    }

    /** Crea un cliente sin conexiones, con un External ID nuevo para su futura conexión AWS. */
    public Tenant register(String id, String name, String email) {
        return tenants.save(Tenant.register(id, name.trim(), email, Ids.newExternalId(), clock.instant()));
    }

    public List<Tenant> list() {
        return tenants.findAll().stream()
                .sorted(Comparator.comparing(Tenant::createdAt).reversed())
                .toList();
    }

    public Tenant get(String id) {
        return tenants.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
    }
}
