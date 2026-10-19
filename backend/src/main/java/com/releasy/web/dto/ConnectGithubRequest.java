package com.releasy.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Lo que GitHub entrega al portal al volver de la instalación o autorización de la app (D-26).
 *
 * @param code           código OAuth de un solo uso (caduca a los 10 minutos)
 * @param installationId instalación recién hecha; vacío si el cliente solo autorizó
 */
public record ConnectGithubRequest(@NotBlank String code, @Positive Long installationId) {
}
