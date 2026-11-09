package com.personaltracker.capture.infrastructure.web;

import com.personaltracker.capture.application.usecase.ReceiveNotification;
import com.personaltracker.capture.domain.model.RawNotification;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada de la captura (ADR-0017). La llama el atajo del iPhone con un token de dispositivo con el
 * permiso {@code finance:capture}; el dueño sale del token, nunca del cuerpo. Responde 202 sin
 * cuerpo, también ante un reenvío exacto, para que el atajo termine rápido.
 */
@RestController
@RequestMapping("/api/v1/device/capture")
class CaptureController {

    private final ReceiveNotification receiveNotification;

    CaptureController(ReceiveNotification receiveNotification) {
        this.receiveNotification = receiveNotification;
    }

    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void receive(@CurrentUser UserId owner, @Valid @RequestBody NotificationRequest body) {
        receiveNotification.execute(owner,
                new RawNotification(body.app(), body.title(), body.body(), body.receivedAt().toInstant()));
    }

    /**
     * Notificación tal como la entrega Atajos.
     *
     * @param app app de origen, por ejemplo "Mensajes", "Rappi" o "Wallet"
     * @param title título o remitente; puede venir vacío
     * @param receivedAt hora de llegada al iPhone en ISO 8601 con zona, por ejemplo
     *     {@code 2026-11-09T10:31:00-05:00}
     */
    record NotificationRequest(
            @NotBlank @Size(max = 100) String app,
            @Size(max = 500) String title,
            @NotBlank @Size(max = 4000) String body,
            @NotNull OffsetDateTime receivedAt) {}
}
