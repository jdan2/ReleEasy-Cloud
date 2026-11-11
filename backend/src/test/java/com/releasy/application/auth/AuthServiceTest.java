package com.releasy.application.auth;

import com.releasy.application.shared.ConflictException;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.tenant.ConnectionStatus;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.user.Role;
import com.releasy.domain.user.User;
import com.releasy.infrastructure.adapters.memory.InMemoryTenantRepository;
import com.releasy.infrastructure.adapters.memory.InMemoryUserRepository;
import com.releasy.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryTenantRepository tenants = new InMemoryTenantRepository();
    /** Coste 4 (el mínimo) para que las pruebas sean rápidas; en la API se usa el de por defecto. */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthService auth = new AuthService(users, new TenantService(tenants, clock), encoder,
            user -> "token-" + user.id(), new AuthService.AdminCredentials("admin", "clave-admin"), clock);

    @Test
    void registrarCreaElClienteSinConexionesYElUsuarioConLaContrasenaCifrada() {
        Session session = auth.register("  Ferretería López ", "  IT@Ferreteria-Lopez.ES ", "secreta123");

        User user = session.user();
        assertThat(user.email()).isEqualTo("it@ferreteria-lopez.es");
        assertThat(user.name()).isEqualTo("Ferretería López");
        assertThat(user.role()).isEqualTo(Role.CLIENT);
        assertThat(user.passwordHash()).isNotEqualTo("secreta123");
        assertThat(encoder.matches("secreta123", user.passwordHash())).isTrue();
        assertThat(session.token()).isEqualTo("token-" + user.id());

        Tenant tenant = tenants.findById(user.tenantId()).orElseThrow();
        assertThat(tenant.name()).isEqualTo("Ferretería López");
        assertThat(tenant.aws().status()).isEqualTo(ConnectionStatus.NOT_CONNECTED);
        assertThat(tenant.aws().externalId()).hasSize(36);
        assertThat(tenant.github().status()).isEqualTo(ConnectionStatus.NOT_CONNECTED);
    }

    @Test
    void noSePuedeRegistrarDosVecesElMismoEmailNiQuedaUnClienteHuerfano() {
        auth.register("Acme", "acme@example.com", "secreta123");

        assertThatThrownBy(() -> auth.register("Otra", "ACME@example.com", "otra-clave"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> auth.register("Otra", "admin", "otra-clave"))
                .isInstanceOf(ConflictException.class);
        assertThat(tenants.findAll()).hasSize(1);
    }

    @Test
    void loginDeClienteConEmailSinNormalizar() {
        auth.register("Acme", "acme@example.com", "secreta123");

        Session session = auth.login(" Acme@Example.com", "secreta123");

        assertThat(session.user().email()).isEqualTo("acme@example.com");
        assertThat(session.user().tenantId()).startsWith("t-");
    }

    @Test
    void loginFallaConContrasenaIncorrectaOEmailDesconocido() {
        auth.register("Acme", "acme@example.com", "secreta123");

        assertThatThrownBy(() -> auth.login("acme@example.com", "incorrecta"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> auth.login("nadie@example.com", "secreta123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void elAdministradorEntraConLasCredencialesDeLaConfiguracion() {
        Session session = auth.login("admin", "clave-admin");

        assertThat(session.user().role()).isEqualTo(Role.ADMIN);
        assertThat(session.user().tenantId()).isNull();
        assertThatThrownBy(() -> auth.login("admin", "otra")).isInstanceOf(InvalidCredentialsException.class);
    }
}
