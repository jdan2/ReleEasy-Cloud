package com.releasy.infrastructure.entrypoints;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.releasy.application.port.TokenIssuer;
import com.releasy.infrastructure.config.ReleasyProperties;
import com.releasy.infrastructure.adapters.security.JwtTokenIssuer;
import com.releasy.domain.user.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * Seguridad de la API (D-24): sesión con JWT (Bearer) firmado con HMAC-SHA256 y dos roles.
 *
 * <ul>
 *   <li>{@code /api/auth/register} y {@code /api/auth/login} son públicos: emiten el token.</li>
 *   <li>{@code /api/me/**} es solo para clientes ({@code CLIENT}); el cliente sale del token.</li>
 *   <li>{@code /api/tenants/**} es solo para el administrador ({@code ADMIN}).</li>
 *   <li>{@code POST /api/webhooks/github} es público: lo autentica la firma HMAC, no un usuario.</li>
 *   <li>{@code /actuator/health} es público para el health check del balanceador.</li>
 *   <li>Sin CSRF ni sesión de servidor: cada petición lleva su token.</li>
 *   <li>Un 401 no envía {@code WWW-Authenticate}; el portal tiene su propio formulario de login.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/github").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/me/**").hasRole(Role.CLIENT.name())
                        .requestMatchers("/api/tenants/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleFromClaim()))
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized));
        return http.build();
    }

    /** Convierte el claim {@code role} del token en la autoridad {@code ROLE_<rol>} de Spring Security. */
    private static JwtAuthenticationConverter roleFromClaim() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenIssuer.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    JwtEncoder jwtEncoder(ReleasyProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    /** Comprueba firma, caducidad y emisor de los tokens que llegan. */
    @Bean
    JwtDecoder jwtDecoder(ReleasyProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtTokenIssuer.ISSUER));
        return decoder;
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder encoder, ReleasyProperties properties, Clock clock) {
        return new JwtTokenIssuer(encoder, properties.auth().tokenTtl(), clock);
    }

    private static SecretKey signingKey(ReleasyProperties properties) {
        return new SecretKeySpec(properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** BCrypt (con el prefijo {@code {bcrypt}}, para poder cambiar de algoritmo en el futuro). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ReleasyProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> origins = properties.cors().allowedOrigins();
        cors.setAllowedOrigins(origins == null ? List.of() : origins);
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
