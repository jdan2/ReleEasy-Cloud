package com.releasy.application.tenant;

import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantStatus;
import com.releasy.infrastructure.memory.InMemoryTenantRepository;
import com.releasy.infrastructure.mock.MockAccountProvisioner;
import com.releasy.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountCreationPollerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
    private final InMemoryTenantRepository tenants = new InMemoryTenantRepository();
    private final MockAccountProvisioner provisioner =
            new MockAccountProvisioner(clock, Duration.ofSeconds(10), "OrganizationAccountAccessRole");
    private final TenantService tenantService = new TenantService(tenants, provisioner, clock);
    private final AccountCreationPoller poller = new AccountCreationPoller(tenants, provisioner, clock, Duration.ofMinutes(30));

    @Test
    void elClientePasaAActiveCuandoAwsTerminaDeCrearLaCuenta() {
        Tenant tenant = tenantService.create("Acme Comercio", "acme@example.com");
        assertThat(tenant.status()).isEqualTo(TenantStatus.CREATING);

        poller.pollPendingAccounts();
        assertThat(tenants.findById(tenant.id()).orElseThrow().status()).isEqualTo(TenantStatus.CREATING);

        clock.advance(Duration.ofSeconds(11));
        poller.pollPendingAccounts();

        Tenant active = tenants.findById(tenant.id()).orElseThrow();
        assertThat(active.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(active.accountId()).hasSize(12);
        assertThat(active.roleArn()).isEqualTo("arn:aws:iam::" + active.accountId() + ":role/OrganizationAccountAccessRole");
    }

    @Test
    void elClientePasaAFailedSiAwsRechazaLaCuenta() {
        Tenant tenant = tenantService.create("Acme", "acme+fail@example.com");

        clock.advance(Duration.ofSeconds(11));
        poller.pollPendingAccounts();

        Tenant failed = tenants.findById(tenant.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(TenantStatus.FAILED);
        assertThat(failed.failureReason()).contains("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void elClientePasaAFailedSiSeAgotaElTiempoDeEspera() {
        AccountCreationPoller impatient = new AccountCreationPoller(tenants, provisioner, clock, Duration.ofSeconds(5));
        Tenant tenant = tenantService.create("Acme", "acme@example.com");

        clock.advance(Duration.ofSeconds(6));
        impatient.pollPendingAccounts();

        assertThat(tenants.findById(tenant.id()).orElseThrow().failureReason()).contains("Tiempo de espera agotado");
    }
}
