package com.releasy.infrastructure.entrypoints;

import com.fasterxml.jackson.databind.JsonNode;
import com.releasy.application.shared.NotFoundException;
import com.releasy.application.template.TemplateCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de plantillas (cualquier usuario autenticado). Se devuelve tal cual lo define el portal. */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateCatalog catalog;

    public TemplateController(TemplateCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<JsonNode> list() {
        return catalog.all();
    }

    @GetMapping("/{id}")
    JsonNode get(@PathVariable String id) {
        return catalog.find(id).orElseThrow(() -> new NotFoundException("Plantilla", id));
    }
}
