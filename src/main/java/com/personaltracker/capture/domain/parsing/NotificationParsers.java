package com.personaltracker.capture.domain.parsing;

import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.RawNotification;
import java.util.List;
import java.util.Optional;

/**
 * Todos los analizadores. Una notificación de una fuente sin analizador también es
 * {@code Unrecognized}: va a revisión en lugar de perderse.
 */
public final class NotificationParsers {

    private final List<NotificationParser> parsers;

    public NotificationParsers(List<NotificationParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public static NotificationParsers all() {
        return new NotificationParsers(List.of(new BancolombiaSmsParser(), new RappiPushParser(), new WalletPushParser()));
    }

    public ParseOutcome parse(RawNotification notification) {
        return parsers.stream()
                .map(parser -> parser.parse(notification))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(ParseOutcome.Unrecognized::new);
    }
}
