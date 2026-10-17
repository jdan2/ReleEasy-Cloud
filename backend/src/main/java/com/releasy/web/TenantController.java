package com.releasy.web;

import com.releasy.application.app.AppService;
import com.releasy.application.tenant.TenantService;
import com.releasy.web.dto.AppResponse;
import com.releasy.web.dto.CreateAppRequest;
import com.releasy.web.dto.CreateTenantRequest;
import com.releasy.web.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final AppService appService;

    public TenantController(TenantService tenantService, AppService appService) {
        this.tenantService = tenantService;
        this.appService = appService;
    }

    /**
     * Da de alta un cliente y solicita su cuenta AWS. Responde enseguida con el cliente en
     * {@code CREATING}; el estado cambia a {@code ACTIVE} o {@code FAILED} en segundo plano.
     */
    @PostMapping
    ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse tenant = TenantResponse.from(tenantService.create(request.name(), request.email()));
        return ResponseEntity.created(URI.create("/api/tenants/" + tenant.id())).body(tenant);
    }

    @GetMapping
    List<TenantResponse> list() {
        return tenantService.list().stream().map(TenantResponse::from).toList();
    }

    @GetMapping("/{id}")
    TenantResponse get(@PathVariable String id) {
        return TenantResponse.from(tenantService.get(id));
    }

    @PostMapping("/{id}/apps")
    ResponseEntity<AppResponse> registerApp(@PathVariable String id, @Valid @RequestBody CreateAppRequest request) {
        AppResponse app = AppResponse.from(appService.register(id, request.name(), request.repository(),
                request.branchOrDefault(), request.plan()));
        return ResponseEntity.created(URI.create("/api/apps/" + app.id())).body(app);
    }

    @GetMapping("/{id}/apps")
    List<AppResponse> listApps(@PathVariable String id) {
        return appService.listByTenant(id).stream().map(AppResponse::from).toList();
    }
}
