package com.releasy.web;

import com.releasy.application.app.AppService;
import com.releasy.application.release.ReleaseService;
import com.releasy.web.dto.AppResponse;
import com.releasy.web.dto.ReleaseResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    AppResponse get(@PathVariable String id) {
        return AppResponse.from(appService.get(id));
    }

    /** Historial de releases, del más reciente al más antiguo. */
    @GetMapping("/{id}/releases")
    List<ReleaseResponse> releases(@PathVariable String id) {
        return releaseService.listByApp(id).stream().map(ReleaseResponse::from).toList();
    }
}
