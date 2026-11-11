package com.releasy.application.aws;

import com.releasy.application.shared.InvalidRequestException;
import com.releasy.application.shared.NotFoundException;
import com.releasy.application.port.AwsConnectionVerifier;
import com.releasy.domain.tenant.AwsConnection;
import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * Conexión de la cuenta AWS del cliente: el cliente crea el rol con la plantilla de CloudFormation,
 * pega su ARN y la plataforma comprueba que puede asumirlo con el External ID (D-18).
 */
public class AwsConnectionService {

    /** Mismo formato que valida el portal. */
    private static final Pattern ROLE_ARN = Pattern.compile("^arn:aws:iam::\\d{12}:role/[\\w+=,.@/-]{1,64}$");

    private final TenantRepository tenants;
    private final AwsConnectionVerifier verifier;
    private final Clock clock;

    public AwsConnectionService(TenantRepository tenants, AwsConnectionVerifier verifier, Clock clock) {
        this.tenants = tenants;
        this.verifier = verifier;
        this.clock = clock;
    }

    public Tenant connect(String tenantId, String roleArn) {
        Tenant tenant = get(tenantId);
        String arn = roleArn == null ? "" : roleArn.trim();
        if (!ROLE_ARN.matcher(arn).matches()) {
            throw new InvalidRequestException(
                    "El ARN del rol no es válido. Formato: arn:aws:iam::<12 dígitos>:role/<nombre>");
        }
        String accountId = verifier.verify(arn, tenant.aws().externalId());
        return tenants.save(tenant.withAws(tenant.aws().connect(arn, accountId, clock.instant()), clock.instant()));
    }

    /** Olvida el rol. El External ID se conserva, así que el cliente puede volver a conectar el mismo rol. */
    public Tenant disconnect(String tenantId) {
        Tenant tenant = get(tenantId);
        return tenants.save(tenant.withAws(AwsConnection.notConnected(tenant.aws().externalId()), clock.instant()));
    }

    private Tenant get(String tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Cliente", tenantId));
    }
}
