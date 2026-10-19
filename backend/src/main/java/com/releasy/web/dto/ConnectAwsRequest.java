package com.releasy.web.dto;

import jakarta.validation.constraints.NotBlank;

/** @param roleArn ARN del rol {@code ReleEasyDeployRole} creado con la plantilla de CloudFormation */
public record ConnectAwsRequest(@NotBlank String roleArn) {
}
