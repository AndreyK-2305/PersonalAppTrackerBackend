package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.domain.model.CaptureChannel;
import com.personaltracker.capture.domain.model.CaptureInstitution;
import com.personaltracker.capture.domain.model.CaptureSource;
import com.personaltracker.capture.domain.model.MovementDirection;
import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.ParsedMovement;
import com.personaltracker.capture.domain.model.ProductHint;
import com.personaltracker.capture.domain.model.ProductType;
import com.personaltracker.capture.domain.model.RawNotification;
import com.personaltracker.capture.domain.parsing.AmountFormat;
import com.personaltracker.capture.domain.parsing.NotificationParser;
import com.personaltracker.shared.domain.Money;
import java.util.Currency;
import java.util.Optional;

/**
 * Analizador de Wallet solo para pruebas, mientras no haya ejemplos reales de su notificación. El
 * título es el comercio y el texto, "{monto}|{últimos 4}", con el monto como lo muestra Wallet
 * ({@code 9.780}). Como Wallet, no trae hora: se usa la de recepción.
 */
final class FakeWalletParser implements NotificationParser {

    @Override
    public Optional<ParseOutcome> parse(RawNotification notification) {
        if (!notification.app().equals("Wallet")) {
            return Optional.empty();
        }
        String[] parts = notification.body().split("\\|");
        Money amount = new Money(AmountFormat.SPANISH.parse(parts[0]).orElseThrow(), Currency.getInstance("COP"));
        return Optional.of(new ParseOutcome.Recognized(new ParsedMovement(
                CaptureSource.APPLE_WALLET,
                "FAKE_WALLET_PURCHASE",
                MovementDirection.OUTFLOW,
                ProductHint.of(CaptureInstitution.BANCOLOMBIA, ProductType.CREDIT_CARD, parts[1]),
                amount,
                Optional.empty(),
                notification.title(),
                CaptureChannel.CARD,
                Optional.empty(),
                Optional.empty())));
    }
}
