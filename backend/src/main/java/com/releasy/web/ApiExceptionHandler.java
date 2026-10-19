package com.releasy.web;

import com.releasy.application.ConflictException;
import com.releasy.application.InvalidRequestException;
import com.releasy.application.NotFoundException;
import com.releasy.application.auth.InvalidCredentialsException;
import com.releasy.application.port.AwsConnectionVerifier.AwsConnectionException;
import com.releasy.application.port.GithubApiException;
import com.releasy.application.webhook.InvalidSignatureException;
import com.releasy.application.webhook.InvalidWebhookPayloadException;
import com.releasy.domain.release.IllegalReleaseTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones de la aplicación a respuestas HTTP con formato RFC 9457 (Problem Details).
 * Los errores de validación de las peticiones los gestiona la clase base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ConflictException.class, IllegalReleaseTransitionException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({InvalidRequestException.class, AwsConnectionException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** GitHub falló o no respondió: es un problema de un servicio externo, no de la petición. */
    @ExceptionHandler(GithubApiException.class)
    ProblemDetail githubError(GithubApiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Error al consultar GitHub: " + e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidSignatureException.class)
    ProblemDetail invalidSignature(InvalidSignatureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidWebhookPayloadException.class)
    ProblemDetail invalidPayload(InvalidWebhookPayloadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
