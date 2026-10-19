package com.releasy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.releasy.application.port.GithubGateway.Repository;

import java.util.List;

/** Repositorio tal como lo espera el portal (tipo {@code GithubRepository} de {@code core/api/models.ts}). */
public record GithubRepositoryResponse(
        String fullName,
        String description,
        @JsonProperty("private") boolean isPrivate,
        String defaultBranch,
        List<String> branches) {

    public static GithubRepositoryResponse from(Repository r) {
        return new GithubRepositoryResponse(r.fullName(), r.description(), r.isPrivate(), r.defaultBranch(), r.branches());
    }
}
