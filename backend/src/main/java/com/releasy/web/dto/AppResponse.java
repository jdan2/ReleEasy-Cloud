package com.releasy.web.dto;

import com.releasy.domain.app.App;
import com.releasy.domain.app.AppPlan;

import java.time.Instant;

public record AppResponse(
        String id,
        String tenantId,
        String name,
        String repository,
        String branch,
        AppPlan plan,
        String url,
        Instant createdAt,
        Instant updatedAt) {

    public static AppResponse from(App a) {
        return new AppResponse(a.id(), a.tenantId(), a.name(), a.repository(), a.branch(), a.plan(), a.url(),
                a.createdAt(), a.updatedAt());
    }
}
