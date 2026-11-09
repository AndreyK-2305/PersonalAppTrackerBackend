package com.personaltracker.capture.domain.model;

import java.util.Objects;

/** Resultado de analizar una notificación. */
public sealed interface ParseOutcome {

    /** Una plantilla de movimiento coincidió exactamente. */
    record Recognized(ParsedMovement movement) implements ParseOutcome {
        public Recognized {
            Objects.requireNonNull(movement, "movement");
        }
    }

    /** Una plantilla de descarte coincidió: el aviso no es un movimiento (por ejemplo, una inscripción). */
    record NotAMovement(String templateId) implements ParseOutcome {
        public NotAMovement {
            Objects.requireNonNull(templateId, "templateId");
        }
    }

    /** Ninguna plantilla coincidió exactamente: la notificación va a revisión, no se interpreta. */
    record Unrecognized() implements ParseOutcome {}
}
