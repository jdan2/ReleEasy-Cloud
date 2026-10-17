package com.releasy.application.tenant;

import com.releasy.application.port.AccountProvisioner;
import com.releasy.application.port.AccountProvisioner.AccountCreationStatus;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.domain.tenant.TenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Completa la creación de cuentas AWS consultando periódicamente {@code DescribeCreateAccountStatus}
 * para todos los clientes en {@code CREATING}.
 *
 * <p>Recorre el repositorio en cada pasada en lugar de mantener tareas en memoria, así que sigue
 * funcionando si la API se reinicia a mitad de una creación. La planificación periódica se configura
 * en la capa de infraestructura.
 */
public class AccountCreationPoller {

    private static final Logger log = LoggerFactory.getLogger(AccountCreationPoller.class);

    private final TenantRepository tenants;
    private final AccountProvisioner provisioner;
    private final Clock clock;
    private final Duration timeout;

    public AccountCreationPoller(TenantRepository tenants, AccountProvisioner provisioner, Clock clock, Duration timeout) {
        this.tenants = tenants;
        this.provisioner = provisioner;
        this.clock = clock;
        this.timeout = timeout;
    }

    public void pollPendingAccounts() {
        for (Tenant tenant : tenants.findByStatus(TenantStatus.CREATING)) {
            try {
                poll(tenant);
            } catch (RuntimeException e) {
                log.warn("Error consultando la creación de la cuenta del cliente {}", tenant.id(), e);
            }
        }
    }

    private void poll(Tenant tenant) {
        Instant now = clock.instant();
        if (tenant.creationRequestId() != null) {
            AccountCreationStatus status = provisioner.describeAccountCreation(tenant.creationRequestId());
            switch (status.state()) {
                case SUCCEEDED -> {
                    tenants.save(tenant.activate(status.accountId(), status.roleArn(), now));
                    log.info("Cuenta AWS {} activa para el cliente {}", status.accountId(), tenant.id());
                    return;
                }
                case FAILED -> {
                    tenants.save(tenant.fail(status.failureReason(), now));
                    log.warn("Falló la creación de la cuenta del cliente {}: {}", tenant.id(), status.failureReason());
                    return;
                }
                case IN_PROGRESS -> {
                    // se comprueba el tiempo de espera a continuación
                }
            }
        }
        if (now.isAfter(tenant.createdAt().plus(timeout))) {
            tenants.save(tenant.fail("Tiempo de espera agotado creando la cuenta AWS (" + timeout + ")", now));
        }
    }
}
