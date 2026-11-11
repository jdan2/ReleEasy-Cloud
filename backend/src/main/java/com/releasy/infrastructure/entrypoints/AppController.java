package com.releasy.infrastructure.entrypoints;

import com.releasy.application.shared.NotFoundException;
import com.releasy.application.app.AppService;
import com.releasy.application.release.ReleaseService;
import com.releasy.domain.app.App;
import com.releasy.infrastructure.entrypoints.dto.AppResponse;
import com.releasy.infrastructure.entrypoints.dto.ReleaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Detalle de una aplicación, sus releases y redespliegue manual. Solo pueden verla su propietario y el administrador; para
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

    /** Redespliega a mano el último commit de la rama. 409 si ya tiene un release en curso o correcto. */
    @PostMapping("/{id}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    ReleaseResponse redeploy(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        visibleApp(jwt, id);
        return ReleaseResponse.from(appService.redeploy(id));
    }

    private App visibleApp(Jwt jwt, String id) {
        App app = appService.get(id);
        if (!CurrentUser.isAdmin(jwt) && !app.tenantId().equals(CurrentUser.tenantId(jwt))) {
            throw new NotFoundException("Aplicación", id);
        }
        return app;
    }
}
