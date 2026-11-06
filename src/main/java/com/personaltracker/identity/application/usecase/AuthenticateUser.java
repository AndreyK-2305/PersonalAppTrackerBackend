package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.dto.AuthenticateCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.AccountLockedException;
import com.personaltracker.identity.domain.exception.InvalidCredentialsException;
import com.personaltracker.identity.domain.exception.InvalidEmailException;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica las credenciales y aplica el bloqueo por intentos fallidos (ADR-0011). Una cuenta
 * inexistente y una contraseña incorrecta producen el mismo error y tardan lo mismo, para no
 * revelar qué correos están registrados.
 */
@Service
public class AuthenticateUser {

    private static final Logger log = LoggerFactory.getLogger(AuthenticateUser.class);

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    // Hash contra el que se compara cuando la cuenta no existe, para igualar el tiempo de respuesta.
    private final String timingEqualizationHash;

    public AuthenticateUser(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.timingEqualizationHash = passwordHasher.hash("timing-equalization-only");
    }

    // Los intentos fallidos deben quedar guardados aunque la operación termine con error.
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public UserProfile execute(AuthenticateCommand command) {
        Optional<User> found = findByEmail(command.email());
        if (found.isEmpty()) {
            passwordHasher.matches(command.password(), timingEqualizationHash);
            log.info("Inicio de sesión fallido: no existe una cuenta con ese correo");
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            log.info("Inicio de sesión rechazado: la cuenta {} está bloqueada", user.id());
            throw new AccountLockedException();
        }

        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            user.recordFailedLogin(now);
            users.save(user);
            if (user.isLocked(now)) {
                log.warn("Cuenta {} bloqueada tras {} intentos fallidos", user.id(), User.MAX_FAILED_ATTEMPTS);
                throw new AccountLockedException();
            }
            log.info("Inicio de sesión fallido para la cuenta {}", user.id());
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin(now);
        users.save(user);
        log.info("Inicio de sesión exitoso de la cuenta {}", user.id());
        return UserProfile.from(user);
    }

    private Optional<User> findByEmail(String rawEmail) {
        try {
            return users.findByEmail(new Email(rawEmail));
        } catch (InvalidEmailException e) {
            return Optional.empty();
        }
    }
}
