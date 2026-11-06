package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.dto.RegisterUserCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.RegistrationPolicy;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.EmailAlreadyRegisteredException;
import com.personaltracker.identity.domain.exception.RegistrationClosedException;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.PasswordPolicy;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crea una cuenta nueva, solo si el registro está habilitado (ADR-0012). */
@Service
public class RegisterUser {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final RegistrationPolicy registrationPolicy;
    private final Clock clock;

    public RegisterUser(
            UserRepository users,
            PasswordHasher passwordHasher,
            RegistrationPolicy registrationPolicy,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.registrationPolicy = registrationPolicy;
        this.clock = clock;
    }

    /** Indica si el registro está habilitado, para que la interfaz ofrezca o no crear una cuenta. */
    public boolean isAvailable() {
        return registrationPolicy.isRegistrationOpen();
    }

    @Transactional
    public UserProfile execute(RegisterUserCommand command) {
        if (!registrationPolicy.isRegistrationOpen()) {
            throw new RegistrationClosedException();
        }
        Email email = new Email(command.email());
        PasswordPolicy.check(command.password());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.register(UserId.newId(), email, passwordHasher.hash(command.password()),
                command.displayName(), clock.instant());
        users.save(user);
        return UserProfile.from(user);
    }
}
