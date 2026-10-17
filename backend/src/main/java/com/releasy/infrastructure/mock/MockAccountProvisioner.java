package com.releasy.infrastructure.mock;

import com.releasy.application.port.AccountProvisioner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simula AWS Organizations: cada petición queda {@code IN_PROGRESS} durante {@code creationDelay} y
 * después termina bien, con un ID de cuenta ficticio de 12 dígitos.
 *
 * <p>Para la demo de errores, si el email contiene {@code +fail} (p. ej. {@code demo+fail@acme.com})
 * la creación termina en {@code FAILED} con el motivo {@code EMAIL_ALREADY_EXISTS}.
 */
public class MockAccountProvisioner implements AccountProvisioner {

    private final Clock clock;
    private final Duration creationDelay;
    private final String roleName;
    private final Map<String, Request> requests = new ConcurrentHashMap<>();

    public MockAccountProvisioner(Clock clock, Duration creationDelay, String roleName) {
        this.clock = clock;
        this.creationDelay = creationDelay;
        this.roleName = roleName;
    }

    @Override
    public String requestAccountCreation(String accountName, String email) {
        String requestId = "car-mock-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        requests.put(requestId, new Request(clock.instant(), email.contains("+fail")));
        return requestId;
    }

    @Override
    public AccountCreationStatus describeAccountCreation(String requestId) {
        Request request = requests.get(requestId);
        if (request == null) {
            return AccountCreationStatus.failed("Petición desconocida (¿se reinició la API en modo mock?)");
        }
        if (clock.instant().isBefore(request.requestedAt().plus(creationDelay))) {
            return AccountCreationStatus.inProgress();
        }
        if (request.shouldFail()) {
            return AccountCreationStatus.failed("EMAIL_ALREADY_EXISTS (simulado)");
        }
        String accountId = fakeAccountId(requestId);
        return AccountCreationStatus.succeeded(accountId, "arn:aws:iam::" + accountId + ":role/" + roleName);
    }

    private static String fakeAccountId(String requestId) {
        long n = Math.abs((long) requestId.hashCode()) % 1_000_000_000_000L;
        return String.format("%012d", n);
    }

    private record Request(Instant requestedAt, boolean shouldFail) {
    }
}
