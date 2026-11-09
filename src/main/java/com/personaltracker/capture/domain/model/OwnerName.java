package com.personaltracker.capture.domain.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nombres y apellidos del usuario, para reconocer transferencias a sí mismo (ADR-0017).
 *
 * <p>Un nombre coincide si todas sus palabras están entre los nombres y apellidos del usuario, sin
 * importar mayúsculas, tildes ni orden, e incluye al menos un nombre y al menos un apellido. Para
 * "Ana María" "Pérez Gómez" coinciden "María Pérez" y "ANA MARIA PEREZ GOMEZ"; no coinciden "Ana"
 * ni "Ana López". Un homónimo como "Ana Pérez" sí coincide: por eso la
 * coincidencia de nombre solo propone la transferencia y el usuario la confirma.
 */
public record OwnerName(Set<String> givenNames, Set<String> surnames) {

    public OwnerName {
        givenNames = Set.copyOf(givenNames);
        surnames = Set.copyOf(surnames);
    }

    /** A partir de los nombres y apellidos tal como los escribe el usuario en su perfil. */
    public static OwnerName of(String givenNames, String surnames) {
        return new OwnerName(Set.copyOf(words(givenNames)), Set.copyOf(words(surnames)));
    }

    public boolean isEmpty() {
        return givenNames.isEmpty() || surnames.isEmpty();
    }

    public boolean matches(String candidate) {
        if (isEmpty()) {
            return false;
        }
        List<String> words = words(candidate);
        if (words.isEmpty()) {
            return false;
        }
        boolean allKnown = words.stream().allMatch(word -> givenNames.contains(word) || surnames.contains(word));
        boolean hasGivenName = words.stream().anyMatch(givenNames::contains);
        boolean hasSurname = words.stream().anyMatch(surnames::contains);
        return allKnown && hasGivenName && hasSurname;
    }

    /** Palabras en minúsculas y sin tildes; los signos y números separan palabras. */
    private static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());
    }
}
