package com.releasy.web.dto;

import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseStatus;

import java.time.Instant;

public record ReleaseResponse(
        String id,
        String appId,
        String commitSha,
        String commitMessage,
        ReleaseStatus status,
        String url,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static ReleaseResponse from(Release r) {
        return new ReleaseResponse(r.id(), r.appId(), r.commitSha(), r.commitMessage(), r.status(), r.url(),
                r.errorMessage(), r.createdAt(), r.updatedAt());
    }
}
