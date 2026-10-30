package com.releasy.application.auth;

import com.releasy.application.shared.ConflictException;
import com.releasy.application.port.TokenIssuer;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.shared.Ids;
import com.releasy.domain.user.Role;
import com.releasy.domain.user.User;
import com.releasy.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

/**
 * Registro e inicio de sesión (D-24).
 *
 * <ul>
 *   <li>Los clientes se guardan en {@link UserRepository} con la contraseña cifrada (BCrypt).</li>
 *   <li>El administrador no se guarda: sus credenciales llegan por configuración
 *       ({@code RELEASY_ADMIN_USER} y {@code RELEASY_ADMIN_PASSWORD}).</li>
 * </ul>
 *
 * <p>Usa la interfaz {@link PasswordEncoder} de Spring Security Crypto, que no depende del contenedor
 * de Spring, así que esta clase se puede probar con {@code new}.
 */
public class AuthService {

    static final String ADMIN_ID = "admin";
    static final String ADMIN_NAME = "Administrador";

    private final UserRepository users;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final AdminCredentials admin;
    private final Clock clock;

    public AuthService(UserRepository users, TenantService tenantService, PasswordEncoder passwordEncoder,
                       TokenIssuer tokenIssuer, AdminCredentials admin, Clock clock) {
        this.users = users;
        this.tenantService = tenantService;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.admin = admin;
        this.clock = clock;
    }

    /**
     * Registra una empresa cliente y su usuario, y abre su sesión.
     *
     * <p>Primero se reserva el email (operación atómica) y solo después se crea el cliente, para no
     * dejar clientes huérfanos si el email ya existía.
     *
     * @throws ConflictException si el email ya está registrado
     */
    public Session register(String companyName, String email, String password) {
        String normalizedEmail = User.normalizeEmail(email);
        if (normalizedEmail.equals(User.normalizeEmail(admin.username()))) {
            throw new ConflictException("Ya existe una cuenta con ese email");
        }
        Instant now = clock.instant();
        String tenantId = Ids.newTenantId();
        User user = User.client(Ids.newUserId(), normalizedEmail, companyName.trim(), tenantId,
                passwordEncoder.encode(password), now);
        if (!users.saveIfAbsent(user)) {
            throw new ConflictException("Ya existe una cuenta con ese email");
        }
        tenantService.register(tenantId, user.name(), user.email());
        return new Session(tokenIssuer.issue(user), user);
    }

    /**
     * Comprueba las credenciales y abre la sesión.
     *
     * @throws InvalidCredentialsException si el email no existe o la contraseña no coincide
     */
    public Session login(String email, String password) {
        String normalizedEmail = User.normalizeEmail(email);
        if (normalizedEmail.equals(User.normalizeEmail(admin.username()))) {
            return loginAdmin(password);
        }
        User user = users.findByEmail(normalizedEmail)
                .filter(u -> passwordEncoder.matches(password, u.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new Session(tokenIssuer.issue(user), user);
    }

    private Session loginAdmin(String password) {
        // Comparación en tiempo constante para no dar pistas sobre la contraseña.
        boolean matches = MessageDigest.isEqual(
                admin.password().getBytes(StandardCharsets.UTF_8),
                (password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new InvalidCredentialsException();
        }
        User user = new User(ADMIN_ID, admin.username(), ADMIN_NAME, Role.ADMIN, null, null, null);
        return new Session(tokenIssuer.issue(user), user);
    }

    /** Credenciales del administrador, leídas de la configuración. */
    public record AdminCredentials(String username, String password) {
    }
}
