package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.dto.ChangePasswordCommand;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.SessionTerminator;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.CurrentPasswordIncorrectException;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.PasswordPolicy;
import com.personaltracker.identity.domain.model.User;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia la contraseña tras verificar la actual y cierra las demás sesiones del usuario, por si la
 * contraseña anterior estaba comprometida (ADR-0011).
 */
@Service
public class ChangePassword {

    private static final Logger log = LoggerFactory.getLogger(ChangePassword.class);

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionTerminator sessionTerminator;
    private final Clock clock;

    public ChangePassword(
            UserRepository users,
            PasswordHasher passwordHasher,
            SessionTerminator sessionTerminator,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionTerminator = sessionTerminator;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangePasswordCommand command) {
        User user = users.findById(command.owner()).orElseThrow(UserNotFoundException::new);
        if (!passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new CurrentPasswordIncorrectException();
        }
        PasswordPolicy.check(command.newPassword());

        user.changePasswordHash(passwordHasher.hash(command.newPassword()), clock.instant());
        users.save(user);
        sessionTerminator.terminateAllExcept(user.id(), command.currentSessionId());
        log.info("Contraseña cambiada para la cuenta {}; se cerraron sus otras sesiones", user.id());
    }
}
