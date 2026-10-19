package com.releasy.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el catálogo exportado del portal se carga y que la validación da los mismos resultados
 * que {@code validateOptions} del portal (ver {@code template-catalog.spec.ts}).
 */
class TemplateCatalogTest {

    private final TemplateCatalog catalog = load();
    private final JsonNode angular = catalog.find("angular-static").orElseThrow();
    private final JsonNode spring = catalog.find("springboot-api").orElseThrow();

    private static TemplateCatalog load() {
        try (InputStream json = TemplateCatalogTest.class.getResourceAsStream("/catalog/templates.json")) {
            return TemplateCatalog.load(json, new ObjectMapper());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void contieneLasTresPlantillas() {
        assertThat(catalog.all()).extracting(t -> t.path("id").asText())
                .containsExactly("angular-static", "springboot-api", "angular-springboot");
    }

    @Test
    void lasOpcionesPorDefectoSonValidasConEmailDeAvisos() {
        for (JsonNode template : catalog.all()) {
            Map<String, Object> options = defaults(template);
            options.put("notification_email", "ops@acme.es");
            assertThat(catalog.validateOptions(template, options)).as(template.path("id").asText()).isEmpty();
        }
    }

    @Test
    void exigeEmailSiHayAlarmasYDominioSoloSiSeActiva() {
        Map<String, Object> options = defaults(angular);
        assertThat(catalog.validateOptions(angular, options)).containsExactly(
                "Email para alarmas: es obligatorio si activas alguna alarma.");

        options.put("front_alarms", false);
        options.put("custom_domain", true);
        options.put("domain_name", "No Valido");
        assertThat(catalog.validateOptions(angular, options)).singleElement().asString().contains("dominio válido");
    }

    @Test
    void validaRangosMinimoMaximoYVariables() {
        Map<String, Object> options = defaults(spring);
        options.put("backend_alarms", false);
        options.put("min_instances", 0);
        options.put("env_vars", Map.of("mal-nombre", "x", "RELEASY_TOKEN", "y"));

        List<String> errors = catalog.validateOptions(spring, options);

        assertThat(errors).contains("Instancias mínimas: debe estar entre 1 y 10.");
        assertThat(errors).anyMatch(e -> e.contains("mal-nombre"));
        assertThat(errors).anyMatch(e -> e.contains("reservado"));

        options.put("min_instances", 3);
        options.put("max_instances", 2);
        options.put("env_vars", Map.of());
        assertThat(catalog.validateOptions(spring, options))
                .containsExactly("Instancias máximas: no puede ser menor que el mínimo.");
    }

    @Test
    void descartaOpcionesYUsosQueLaPlantillaNoDeclara() {
        Map<String, Object> options = defaults(spring);
        options.put("sqs_queue", true);

        assertThat(catalog.knownOptions(spring, options)).doesNotContainKey("sqs_queue").containsKey("instance_type");
        assertThat(catalog.knownUsage(spring, Map.of("apiRequestsPerMonth", 1000, "pageViewsPerMonth", 5, "responseSizeKb", -1)))
                .containsOnlyKeys("apiRequestsPerMonth");
    }

    /** Equivalente a {@code defaultOptions} del portal. */
    private static Map<String, Object> defaults(JsonNode template) {
        Map<String, Object> options = new HashMap<>();
        for (JsonNode field : TemplateCatalog.fields(template)) {
            JsonNode value = field.get("default");
            options.put(field.path("key").asText(), new ObjectMapper().convertValue(value, Object.class));
        }
        return options;
    }
}
