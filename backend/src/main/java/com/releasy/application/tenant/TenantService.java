package com.releasy.application.tenant;

import com.releasy.application.NotFoundException;
import com.releasy.application.port.AccountProvisioner;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

/**
 * Casos de uso de clientes.
 *
 * <p>La creación es asíncrona: se registra el cliente en {@code CREATING}, se solicita la cuenta a
 * AWS Organizations y se responde enseguida. {@link AccountCreationPoller} completa el proceso.
 */
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenants;
    private final AccountProvisioner provisioner;
    private final Clock clock;

    public TenantService(TenantRepository tenants, AccountProvisioner provisioner, Clock clock) {
        this.tenants = tenants;
        this.provisioner = provisioner;
        this.clock = clock;
    }

    public Tenant create(String name, String email) {
        Tenant tenant = tenants.save(Tenant.creating(Ids.newTenantId(), name.trim(), email.trim(), clock.instant()));
        try {
            String requestId = provisioner.requestAccountCreation(tenant.name(), tenant.email());
            log.info("Solicitada cuenta AWS para el cliente {} (petición {})", tenant.id(), requestId);
            return tenants.save(tenant.withCreationRequest(requestId, clock.instant()));
        } catch (RuntimeException e) {
            log.warn("No se pudo solicitar la cuenta AWS del cliente {}", tenant.id(), e);
            return tenants.save(tenant.fail("No se pudo solicitar la cuenta AWS: " + e.getMessage(), clock.instant()));
        }
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
