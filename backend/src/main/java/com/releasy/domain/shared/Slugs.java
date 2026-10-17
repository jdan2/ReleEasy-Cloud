package com.releasy.domain.shared;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Convierte textos libres (p. ej. el nombre de un cliente) en "slugs" aptos para nombres de
 * recursos de AWS y subdominios: minúsculas, dígitos y guiones.
 */
public final class Slugs {

    private Slugs() {
    }

    public static String of(String text, int maxLength) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "cliente" : slug;
    }
}
