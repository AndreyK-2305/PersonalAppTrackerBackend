package com.personaltracker.capture.infrastructure.web;

import com.personaltracker.capture.application.usecase.ManageOwnKeys;
import com.personaltracker.capture.domain.model.OwnKey;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
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

/** Llaves Bre-B propias del usuario (Ajustes → Perfil, ADR-0017). */
@RestController
@RequestMapping("/api/v1/capture/own-keys")
class OwnKeyController {

    private final ManageOwnKeys manageOwnKeys;

    OwnKeyController(ManageOwnKeys manageOwnKeys) {
        this.manageOwnKeys = manageOwnKeys;
    }

    @GetMapping
    List<OwnKeyResponse> list(@CurrentUser UserId owner) {
        return manageOwnKeys.list(owner).stream().map(OwnKeyResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OwnKeyResponse add(@CurrentUser UserId owner, @Valid @RequestBody OwnKeyRequest body) {
        return OwnKeyResponse.from(manageOwnKeys.add(owner, body.key(), body.productId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentUser UserId owner, @PathVariable UUID id) {
        manageOwnKeys.remove(owner, id);
    }

    /** {@code productId}: cuenta o billetera activa a la que llega el dinero enviado a esa llave. */
    record OwnKeyRequest(@NotBlank @Size(max = OwnKey.MAX_LENGTH) String key, @NotNull UUID productId) {}

    /** La llave se devuelve normalizada: sin espacios a los lados y en minúsculas. */
    @Schema(requiredProperties = {"id", "key", "productId", "createdAt"})
    record OwnKeyResponse(UUID id, String key, UUID productId, Instant createdAt) {

        static OwnKeyResponse from(OwnKey key) {
            return new OwnKeyResponse(key.id(), key.key(), key.productId(), key.createdAt());
        }
    }
}
