package com.releasy.infrastructure.entrypoints;

import com.releasy.application.auth.AuthService;
import com.releasy.infrastructure.entrypoints.dto.LoginRequest;
import com.releasy.infrastructure.entrypoints.dto.RegisterRequest;
import com.releasy.infrastructure.entrypoints.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro e inicio de sesión. Las dos rutas devuelven la sesión ({@code token} y {@code user}); el
 * portal envía después {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Alta de una empresa cliente. Responde 409 si el email ya está registrado. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse register(@Valid @RequestBody RegisterRequest request) {
        return SessionResponse.from(authService.register(request.companyName(), request.email(), request.password()));
    }

    /** Responde 401 si el email o la contraseña no son correctos. */
    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return SessionResponse.from(authService.login(request.email(), request.password()));
    }
}
