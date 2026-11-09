package com.personaltracker.identity.infrastructure.web;

import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.identity.application.usecase.DeviceTokens;
import com.personaltracker.identity.domain.model.DeviceScope;
import com.personaltracker.identity.domain.model.DeviceToken;
import com.personaltracker.identity.domain.model.DeviceTokenId;
import com.personaltracker.shared.domain.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tokens de dispositivo del usuario autenticado (Ajustes → Conexiones, ADR-0017). Se gestionan con
 * la sesión; el secreto solo aparece en la respuesta de creación.
 */
@RestController
@RequestMapping("/api/v1/me/device-tokens")
class DeviceTokenController {

    private final DeviceTokens deviceTokens;

    DeviceTokenController(DeviceTokens deviceTokens) {
        this.deviceTokens = deviceTokens;
    }

    @GetMapping
    List<DeviceTokenResponse> list(@CurrentUser UserId owner) {
        return deviceTokens.list(owner).stream().map(DeviceTokenResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedDeviceTokenResponse create(@CurrentUser UserId owner, @Valid @RequestBody CreateDeviceTokenRequest body) {
        DeviceTokens.IssuedToken issued = deviceTokens.create(owner, body.name(), body.scopes());
        return new CreatedDeviceTokenResponse(DeviceTokenResponse.from(issued.token()), issued.secret());
    }

    /** Revoca el token; es definitivo y responde 204 también si ya estaba revocado. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@CurrentUser UserId owner, @PathVariable UUID id) {
        deviceTokens.revoke(owner, new DeviceTokenId(id));
    }

    record CreateDeviceTokenRequest(
            @NotBlank @Size(max = DeviceToken.MAX_NAME_LENGTH) String name,
            @NotEmpty Set<DeviceScope> scopes) {}

    @Schema(requiredProperties = {"id", "name", "scopes", "active", "createdAt"})
    record DeviceTokenResponse(
            UUID id,
            String name,
            Set<DeviceScope> scopes,
            boolean active,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt) {

        static DeviceTokenResponse from(DeviceToken token) {
            return new DeviceTokenResponse(token.id().value(), token.name(), token.scopes(), token.isActive(),
                    token.createdAt(), token.lastUsedAt(), token.revokedAt());
        }
    }

    /** {@code secret} se muestra una sola vez: no se puede volver a consultar. */
    @Schema(requiredProperties = {"token", "secret"})
    record CreatedDeviceTokenResponse(DeviceTokenResponse token, String secret) {}
}
