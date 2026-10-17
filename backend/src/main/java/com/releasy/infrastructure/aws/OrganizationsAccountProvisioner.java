package com.releasy.infrastructure.aws;

import com.releasy.application.port.AccountProvisioner;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.awssdk.services.organizations.model.IAMUserAccessToBilling;

/**
 * Crea cuentas miembro con AWS Organizations. Debe ejecutarse con credenciales de la cuenta de
 * administración (management account) de la organización.
 *
 * <p>Organizations crea automáticamente en la cuenta nueva el rol {@code roleName}
 * (por defecto {@code OrganizationAccountAccessRole}), que confía en la cuenta de administración. Es
 * el rol que asume el pipeline para desplegar.
 */
public class OrganizationsAccountProvisioner implements AccountProvisioner {

    private final OrganizationsClient organizations;
    private final String roleName;

    public OrganizationsAccountProvisioner(OrganizationsClient organizations, String roleName) {
        this.organizations = organizations;
        this.roleName = roleName;
    }

    @Override
    public String requestAccountCreation(String accountName, String email) {
        return organizations.createAccount(b -> b
                        .accountName(accountName)
                        .email(email)
                        .roleName(roleName)
                        .iamUserAccessToBilling(IAMUserAccessToBilling.DENY))
                .createAccountStatus()
                .id();
    }

    @Override
    public AccountCreationStatus describeAccountCreation(String requestId) {
        CreateAccountStatus status = organizations
                .describeCreateAccountStatus(b -> b.createAccountRequestId(requestId))
                .createAccountStatus();
        return switch (status.state()) {
            case SUCCEEDED -> AccountCreationStatus.succeeded(status.accountId(), roleArn(status.accountId()));
            case FAILED -> AccountCreationStatus.failed(status.failureReasonAsString());
            default -> AccountCreationStatus.inProgress();
        };
    }

    private String roleArn(String accountId) {
        return "arn:aws:iam::" + accountId + ":role/" + roleName;
    }
}
