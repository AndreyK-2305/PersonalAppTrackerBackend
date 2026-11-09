package com.personaltracker.capture.domain.parsing;

import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.RawNotification;
import java.util.Optional;

/** Analizador de una fuente (una entidad por un canal, por ejemplo los SMS de Bancolombia). */
public interface NotificationParser {

    /**
     * El resultado si la notificación viene de esta fuente, o vacío si no le corresponde. Una
     * notificación de la fuente que no encaja en ninguna plantilla devuelve {@code Unrecognized}.
     */
    Optional<ParseOutcome> parse(RawNotification notification);
}
