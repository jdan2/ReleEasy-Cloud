package com.releasy.infrastructure.adapters.security;

import com.releasy.application.port.TokenIssuer;
import com.releasy.domain.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emite JWT firmados con HMAC-SHA256 (D-24).
 *
 * <p>Claims: {@code sub} (ID del usuario), {@code email}, {@code name}, {@code role} y {@code tenantId}
 * (solo para clientes). La API los lee de vuelta en {@code SecurityConfig} y en los controladores.
 */
public class JwtTokenIssuer implements TokenIssuer {

    public static final String ISSUER = "releasy";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TENANT_ID = "tenantId";

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.id())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_NAME, user.name())
                .claim(CLAIM_ROLE, user.role().name());
        if (user.tenantId() != null) {
            claims.claim(CLAIM_TENANT_ID, user.tenantId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
