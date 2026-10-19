package com.releasy.web;

import com.releasy.application.NotFoundException;
import com.releasy.application.app.AppService;
import com.releasy.application.release.ReleaseService;
import com.releasy.domain.app.App;
import com.releasy.web.dto.AppResponse;
import com.releasy.web.dto.ReleaseResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Detalle de una aplicación y sus releases. Solo pueden verla su propietario y el administrador; para
 * cualquier otro cliente responde 404, como si no existiera.
 */
@RestController
@RequestMapping("/api/apps")
public class AppController {

    private final AppService appService;
    private final ReleaseService releaseService;

    public AppController(AppService appService, ReleaseService releaseService) {
        this.appService = appService;
        this.releaseService = releaseService;
    }

    @GetMapping("/{id}")
    AppResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return AppResponse.from(visibleApp(jwt, id));
    }

    /** Historial de releases, del más reciente al más antiguo. */
    @GetMapping("/{id}/releases")
    List<ReleaseResponse> releases(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        visibleApp(jwt, id);
        return releaseService.listByApp(id).stream().map(ReleaseResponse::from).toList();
    }

    private App visibleApp(Jwt jwt, String id) {
        App app = appService.get(id);
        if (!CurrentUser.isAdmin(jwt) && !app.tenantId().equals(CurrentUser.tenantId(jwt))) {
            throw new NotFoundException("Aplicación", id);
        }
        return app;
    }
}
