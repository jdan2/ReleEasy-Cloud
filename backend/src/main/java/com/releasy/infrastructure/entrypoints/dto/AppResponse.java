package com.releasy.infrastructure.entrypoints.dto;

import com.releasy.domain.app.App;

import java.time.Instant;
import java.util.Map;

/** Aplicación tal como la espera el portal (tipo {@code App} de {@code core/api/models.ts}). */
public record AppResponse(
        String id,
        String tenantId,
        String name,
        String templateId,
        String repository,
        String branch,
        Map<String, Object> options,
        Map<String, Double> usage,
        String url,
        Instant createdAt,
        Instant updatedAt) {

    public static AppResponse from(App a) {
        return new AppResponse(a.id(), a.tenantId(), a.name(), a.templateId(), a.repository(), a.branch(),
                a.options(), a.usage(), a.url(), a.createdAt(), a.updatedAt());
    }
}
