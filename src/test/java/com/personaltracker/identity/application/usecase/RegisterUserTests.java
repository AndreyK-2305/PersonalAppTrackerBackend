package com.personaltracker.identity.application.usecase;

import static com.personaltracker.identity.application.IdentityFakes.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.application.IdentityFakes.FakePasswordHasher;
import com.personaltracker.identity.application.IdentityFakes.InMemoryUserRepository;
import com.personaltracker.identity.application.dto.RegisterUserCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.domain.exception.EmailAlreadyRegisteredException;
import com.personaltracker.identity.domain.exception.PasswordPolicyViolationException;
import com.personaltracker.identity.domain.exception.RegistrationClosedException;
import com.personaltracker.identity.domain.model.Email;
import org.junit.jupiter.api.Test;

class RegisterUserTests {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();

    @Test
    void rejectsRegistrationWhenClosed() {
        RegisterUser registerUser = new RegisterUser(users, hasher, () -> false, CLOCK);

        assertThatThrownBy(() -> registerUser.execute(validCommand("kevin@example.com")))
                .isInstanceOf(RegistrationClosedException.class);
        assertThat(users.count()).isZero();
    }

    @Test
    void createsTheAccountWithNormalizedEmailAndHashedPassword() {
        RegisterUser registerUser = new RegisterUser(users, hasher, () -> true, CLOCK);

        UserProfile profile = registerUser.execute(validCommand(" Kevin@Example.com "));

        assertThat(profile.email()).isEqualTo("kevin@example.com");
        assertThat(profile.timeZone()).isEqualTo("America/Bogota");
        assertThat(users.findByEmail(new Email("kevin@example.com")).orElseThrow().passwordHash())
                .isEqualTo("hashed:contraseña-segura-1");
    }

    @Test
    void rejectsAnEmailThatIsAlreadyRegistered() {
        RegisterUser registerUser = new RegisterUser(users, hasher, () -> true, CLOCK);
        registerUser.execute(validCommand("kevin@example.com"));

        assertThatThrownBy(() -> registerUser.execute(validCommand("KEVIN@example.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void rejectsPasswordsOutsideThePolicy() {
        RegisterUser registerUser = new RegisterUser(users, hasher, () -> true, CLOCK);

        assertThatThrownBy(() -> registerUser.execute(new RegisterUserCommand("kevin@example.com", "corta", "Kevin")))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThat(users.count()).isZero();
    }

    private static RegisterUserCommand validCommand(String email) {
        return new RegisterUserCommand(email, "contraseña-segura-1", "Kevin");
    }
}
