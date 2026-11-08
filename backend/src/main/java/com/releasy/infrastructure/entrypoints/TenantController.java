package com.releasy.infrastructure.entrypoints;

import com.releasy.application.app.AppService;
import com.releasy.application.tenant.TenantService;
import com.releasy.domain.tenant.Tenant;
import com.releasy.infrastructure.config.ReleasyProperties;
import com.releasy.infrastructure.entrypoints.dto.AppResponse;
import com.releasy.infrastructure.entrypoints.dto.TenantResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vista del administrador sobre los clientes (solo lectura; los clientes se registran solos).
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final AppService appService;
    private final ReleasyProperties.Aws awsProperties;

    public TenantController(TenantService tenantService, AppService appService, ReleasyProperties properties) {
        this.tenantService = tenantService;
        this.appService = appService;
        this.awsProperties = properties.aws();
    }

    @GetMapping
    List<TenantResponse> list() {
        return tenantService.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    TenantResponse get(@PathVariable String id) {
        return toResponse(tenantService.get(id));
    }

    @GetMapping("/{id}/apps")
    List<AppResponse> listApps(@PathVariable String id) {
        return appService.listByTenant(id).stream().map(AppResponse::from).toList();
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.from(tenant, awsProperties.platformAccountId(), awsProperties.region(),
                awsProperties.roleTemplateUrl(), appService.listByTenant(tenant.id()).size());
    }
}
