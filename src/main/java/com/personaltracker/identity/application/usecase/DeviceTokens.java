package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.port.out.DeviceTokenRepository;
import com.personaltracker.identity.domain.exception.DeviceTokenNotFoundException;
import com.personaltracker.identity.domain.model.DeviceScope;
import com.personaltracker.identity.domain.model.DeviceToken;
import com.personaltracker.identity.domain.model.DeviceTokenId;
import com.personaltracker.identity.domain.model.DeviceTokenSecret;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens de dispositivo (ADR-0017): crear, listar, revocar y autenticar. Se gestionan en Ajustes →
 * Conexiones; el atajo del iPhone los usa para enviar notificaciones.
 */
@Service
public class DeviceTokens {

    /** Token recién creado con su secreto, que solo se muestra en esta respuesta. */
    public record IssuedToken(DeviceToken token, String secret) {}

    /** Dueño autenticado y permisos de un token válido. */
    public record Authenticated(UserId owner, Set<DeviceScope> scopes) {}

    private final DeviceTokenRepository tokens;
    private final Clock clock;

    public DeviceTokens(DeviceTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public IssuedToken create(UserId owner, String name, Set<DeviceScope> scopes) {
        String secret = DeviceTokenSecret.generate();
        DeviceToken token = DeviceToken.issue(DeviceTokenId.newId(), owner, name, DeviceTokenSecret.hash(secret),
                scopes, clock.instant());
        tokens.save(token);
        return new IssuedToken(token, secret);
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> list(UserId owner) {
        return tokens.findAllByOwner(owner);
    }

    /** 404 si el token no existe o es de otro usuario. */
    @Transactional
    public void revoke(UserId owner, DeviceTokenId id) {
        DeviceToken token = tokens.findByIdAndOwner(id, owner).orElseThrow(DeviceTokenNotFoundException::new);
        token.revoke(clock.instant());
        tokens.save(token);
    }

    /**
     * El dueño y los permisos de un secreto válido y no revocado, y anota su último uso. Vacío si el
     * secreto no corresponde a ningún token activo.
     */
    @Transactional
    public Optional<Authenticated> authenticate(String secret) {
        if (secret == null || !secret.startsWith(DeviceTokenSecret.PREFIX)) {
            return Optional.empty();
        }
        return tokens.findBySecretHash(DeviceTokenSecret.hash(secret))
                .filter(DeviceToken::isActive)
                .map(token -> {
                    token.recordUse(clock.instant());
                    tokens.save(token);
                    return new Authenticated(token.owner(), token.scopes());
                });
    }
}
