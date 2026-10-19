package com.releasy.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * Catálogo de plantillas (D-22, D-25).
 *
 * <p>La fuente es {@code frontend/src/app/core/templates/template-catalog.ts}; {@code npm run
 * export:catalog} lo exporta a {@code resources/catalog/templates.json}, que se carga aquí. Así el portal
 * y la API comparten las plantillas, y {@link #validateOptions} aplica las mismas reglas que
 * {@code validateOptions} del portal, con los mismos mensajes.
 *
 * <p>Las plantillas se manejan como {@link JsonNode}: la API las devuelve tal cual y solo lee de ellas
 * los campos que necesita para validar.
 */
public class TemplateCatalog {

    private static final Pattern ENV_VAR_NAME = Pattern.compile("^[A-Z_][A-Z0-9_]*$");
    private static final List<String> ALARM_KEYS = List.of("front_alarms", "backend_alarms");

    private final List<JsonNode> templates;

    public TemplateCatalog(List<JsonNode> templates) {
        this.templates = List.copyOf(templates);
    }

    public static TemplateCatalog load(InputStream json, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return new TemplateCatalog(StreamSupport.stream(root.spliterator(), false).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catálogo de plantillas", e);
        }
    }

    public List<JsonNode> all() {
        return templates;
    }

    public Optional<JsonNode> find(String id) {
        return templates.stream().filter(t -> t.path("id").asText().equals(id)).findFirst();
    }

    /** Todos los campos de opción de la plantilla, de todos sus grupos. */
    static List<JsonNode> fields(JsonNode template) {
        List<JsonNode> fields = new ArrayList<>();
        template.path("optionGroups").forEach(group -> group.path("fields").forEach(fields::add));
        return fields;
    }

    /** Se queda solo con las opciones que declara la plantilla (descarta claves desconocidas y nulos). */
    public Map<String, Object> knownOptions(JsonNode template, Map<String, Object> options) {
        Map<String, Object> known = new LinkedHashMap<>();
        for (JsonNode field : fields(template)) {
            String key = field.path("key").asText();
            if (options != null && options.get(key) != null) {
                known.put(key, options.get(key));
            }
        }
        return known;
    }

    /** Se queda solo con los campos de uso que declara la plantilla y que son números no negativos. */
    public Map<String, Double> knownUsage(JsonNode template, Map<String, Object> usage) {
        Map<String, Double> known = new LinkedHashMap<>();
        for (JsonNode field : template.path("usageFields")) {
            String key = field.path("key").asText();
            if (usage != null && usage.get(key) instanceof Number n && n.doubleValue() >= 0) {
                known.put(key, n.doubleValue());
            }
        }
        return known;
    }

    /** Errores de validación de las opciones (vacío si son válidas). */
    public List<String> validateOptions(JsonNode template, Map<String, Object> options) {
        List<String> errors = new ArrayList<>();
        for (JsonNode field : fields(template)) {
            if (!isVisible(field.get("visibleWhen"), options)) {
                continue;
            }
            String label = field.path("label").asText();
            Object value = options.get(field.path("key").asText());
            switch (field.path("type").asText()) {
                case "number" -> {
                    double min = field.path("min").asDouble();
                    double max = field.path("max").asDouble();
                    if (!(value instanceof Number n) || Double.isNaN(n.doubleValue())
                            || n.doubleValue() < min || n.doubleValue() > max) {
                        errors.add(label + ": debe estar entre " + format(min) + " y " + format(max) + ".");
                    }
                }
                case "text" -> {
                    String text = value instanceof String s ? s.trim() : "";
                    if (field.path("required").asBoolean(false) && text.isEmpty()) {
                        errors.add(label + ": es obligatorio.");
                    } else if (!text.isEmpty() && field.hasNonNull("pattern")
                            && !Pattern.compile(field.get("pattern").asText()).matcher(text).find()) {
                        errors.add(label + ": " + field.path("patternMessage").asText("formato no válido."));
                    }
                }
                case "keyvalue" -> {
                    if (value instanceof Map<?, ?> map) {
                        for (Object key : map.keySet()) {
                            String name = String.valueOf(key);
                            if (!ENV_VAR_NAME.matcher(name).matches()) {
                                errors.add("Variable \"" + name + "\": usa mayúsculas, dígitos y guiones bajos (p. ej. API_TIMEOUT).");
                            } else if (name.startsWith("RELEASY_")) {
                                errors.add("Variable \"" + name + "\": el prefijo RELEASY_ está reservado para la plataforma.");
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        if (options.get("min_instances") instanceof Number min && options.get("max_instances") instanceof Number max
                && min.doubleValue() > max.doubleValue()) {
            errors.add("Instancias máximas: no puede ser menor que el mínimo.");
        }
        boolean alarmsEnabled = ALARM_KEYS.stream().anyMatch(key -> Boolean.TRUE.equals(options.get(key)));
        String email = options.get("notification_email") instanceof String s ? s.trim() : "";
        if (alarmsEnabled && email.isEmpty()) {
            errors.add("Email para alarmas: es obligatorio si activas alguna alarma.");
        }
        return errors;
    }

    /** Equivalente a {@code isVisible} del portal: sin condición, o la opción tiene el valor indicado. */
    static boolean isVisible(JsonNode condition, Map<String, Object> options) {
        if (condition == null || condition.isNull()) {
            return true;
        }
        Object value = options.get(condition.path("key").asText());
        JsonNode expected = condition.path("equals");
        if (expected.isBoolean()) {
            return Objects.equals(value, expected.asBoolean());
        }
        if (expected.isNumber()) {
            return value instanceof Number n && n.doubleValue() == expected.asDouble();
        }
        return Objects.equals(value, expected.asText());
    }

    /** Como en JavaScript: 10 en lugar de 10.0. */
    private static String format(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
