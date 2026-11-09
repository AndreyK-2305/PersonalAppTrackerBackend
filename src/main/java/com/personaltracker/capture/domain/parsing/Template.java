package com.personaltracker.capture.domain.parsing;

import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.ParsedMovement;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantilla de un tipo de mensaje: una expresión regular anclada al inicio y la forma de extraer el
 * movimiento de sus grupos. Las plantillas de descarte reconocen avisos que no son movimientos.
 */
final class Template {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private final String id;
    private final Pattern pattern;
    private final Function<Matcher, Optional<ParsedMovement>> extractor;

    private Template(String id, String regex, Function<Matcher, Optional<ParsedMovement>> extractor) {
        this.id = id;
        this.pattern = Pattern.compile("^" + regex, FLAGS);
        this.extractor = extractor;
    }

    /** Si los grupos no son válidos (monto o fecha mal formados), el extractor devuelve vacío. */
    static Template movement(String id, String regex, Function<Matcher, Optional<ParsedMovement>> extractor) {
        return new Template(id, regex, extractor);
    }

    static Template ignored(String id, String regex) {
        return new Template(id, regex, null);
    }

    String id() {
        return id;
    }

    /**
     * El resultado si el texto encaja en la plantilla, o vacío si no encaja. Un texto que encaja pero
     * con grupos inválidos es {@code Unrecognized}: no se interpreta a medias.
     */
    Optional<ParseOutcome> apply(String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.lookingAt()) {
            return Optional.empty();
        }
        if (extractor == null) {
            return Optional.of(new ParseOutcome.NotAMovement(id));
        }
        return Optional.of(extractor.apply(matcher)
                .<ParseOutcome>map(ParseOutcome.Recognized::new)
                .orElseGet(ParseOutcome.Unrecognized::new));
    }
}
