package com.releasy.web.dto;

import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantStatus;

import java.time.Instant;

public record TenantResponse(
        String id,
        String name,
        String email,
        TenantStatus status,
        String accountId,
        String roleArn,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.id(), t.name(), t.email(), t.status(), t.accountId(), t.roleArn(),
                t.failureReason(), t.createdAt(), t.updatedAt());
    }
}
