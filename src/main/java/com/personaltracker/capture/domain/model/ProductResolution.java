package com.personaltracker.capture.domain.model;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Producto propio que corresponde a una notificación (ADR-0017):
 *
 * <ol>
 *   <li>Con últimos 4 dígitos: los productos activos de esa entidad y tipo con esos dígitos.
 *   <li>Sin dígitos en el texto: los productos activos de esa entidad y tipo.
 * </ol>
 *
 * <p>Uno solo es {@link Matched}; ninguno es {@link Unmatched} (el movimiento va a "Otro"); varios
 * es {@link Ambiguous} (va a "Otro" y a revisión). La entidad se compara sin mayúsculas ni tildes, y
 * basta con que la que escribió el usuario contenga el nombre de la entidad ("Bancolombia S.A.").
 */
public sealed interface ProductResolution {

    record Matched(UUID productId) implements ProductResolution {
        public Matched {
            Objects.requireNonNull(productId, "productId");
        }
    }

    record Unmatched() implements ProductResolution {}

    record Ambiguous() implements ProductResolution {}

    static ProductResolution resolve(ProductHint hint, List<OwnedProduct> products) {
        String institution = simplify(hint.institution().displayName());
        List<OwnedProduct> candidates = products.stream()
                .filter(product -> product.kind().isOfType(hint.type()))
                .filter(product -> product.institution().map(ProductResolution::simplify)
                        .filter(name -> name.contains(institution))
                        .isPresent())
                .filter(product -> hint.lastFourDigits().isEmpty()
                        || hint.lastFourDigits().equals(product.lastFourDigits()))
                .toList();
        return switch (candidates.size()) {
            case 0 -> new Unmatched();
            case 1 -> new Matched(candidates.getFirst().id());
            default -> new Ambiguous();
        };
    }

    private static String simplify(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
