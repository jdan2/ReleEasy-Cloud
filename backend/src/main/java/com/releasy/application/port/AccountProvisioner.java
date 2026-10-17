package com.releasy.application.port;

/**
 * Puerto hacia AWS Organizations para crear cuentas miembro.
 *
 * <p>Implementaciones: {@code OrganizationsAccountProvisioner} (modo real) y
 * {@code MockAccountProvisioner} (modo mock).
 */
public interface AccountProvisioner {

    /**
     * Solicita la creación de una cuenta ({@code organizations:CreateAccount}).
     *
     * @return identificador de la petición, para consultar su estado
     */
    String requestAccountCreation(String accountName, String email);

    /** Consulta el estado de una petición ({@code organizations:DescribeCreateAccountStatus}). */
    AccountCreationStatus describeAccountCreation(String requestId);

    record AccountCreationStatus(State state, String accountId, String roleArn, String failureReason) {

        public enum State { IN_PROGRESS, SUCCEEDED, FAILED }

        public static AccountCreationStatus inProgress() {
            return new AccountCreationStatus(State.IN_PROGRESS, null, null, null);
        }

        public static AccountCreationStatus succeeded(String accountId, String roleArn) {
            return new AccountCreationStatus(State.SUCCEEDED, accountId, roleArn, null);
        }

        public static AccountCreationStatus failed(String reason) {
            return new AccountCreationStatus(State.FAILED, null, null, reason);
        }
    }
}
