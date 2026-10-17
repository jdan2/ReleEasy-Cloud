package com.releasy.web;

import com.releasy.infrastructure.config.ReleasyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Seguridad del MVP: HTTP Basic con un único administrador, configurado por variables de entorno.
 *
 * <ul>
 *   <li>{@code POST /api/webhooks/github} es público: lo autentica la firma HMAC, no un usuario.</li>
 *   <li>{@code /actuator/health} es público para el health check del balanceador.</li>
 *   <li>Sin CSRF ni sesión: la API es sin estado y cada petición lleva sus credenciales.</li>
 *   <li>Un 401 no envía {@code WWW-Authenticate}, para que el navegador no muestre su diálogo de
 *       login; el portal tiene su propio formulario.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/github").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUser(ReleasyProperties properties, PasswordEncoder encoder) {
        ReleasyProperties.Admin admin = properties.admin();
        return new InMemoryUserDetailsManager(User.withUsername(admin.username())
                .password(encoder.encode(admin.password()))
                .roles("ADMIN")
                .build());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ReleasyProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> origins = properties.cors().allowedOrigins();
        cors.setAllowedOrigins(origins == null ? List.of() : origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
