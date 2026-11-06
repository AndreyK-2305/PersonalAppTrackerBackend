package com.personaltracker.identity.application.usecase;

import static com.personaltracker.identity.application.IdentityFakes.CLOCK;
import static com.personaltracker.identity.application.IdentityFakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.application.IdentityFakes.FakePasswordHasher;
import com.personaltracker.identity.application.IdentityFakes.InMemoryUserRepository;
import com.personaltracker.identity.application.dto.AuthenticateCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.domain.exception.AccountLockedException;
import com.personaltracker.identity.domain.exception.InvalidCredentialsException;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticateUserTests {

    private static final String PASSWORD = "contraseña-segura-1";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private AuthenticateUser authenticateUser;
    private User user;

    @BeforeEach
    void setUp() {
        authenticateUser = new AuthenticateUser(users, hasher, CLOCK);
        user = User.register(UserId.newId(), new Email("kevin@example.com"), hasher.hash(PASSWORD), "Kevin", NOW);
        users.save(user);
    }

    @Test
    void returnsTheProfileAndRecordsTheLoginWithValidCredentials() {
        UserProfile profile = authenticateUser.execute(new AuthenticateCommand("Kevin@Example.com", PASSWORD));

        assertThat(profile.id()).isEqualTo(user.id());
        assertThat(profile.lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void unknownEmailFailsLikeAWrongPasswordAndStillChecksAHash() {
        int checksBefore = hasher.matchesCalls();

        assertThatThrownBy(() -> authenticateUser.execute(new AuthenticateCommand("nadie@example.com", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(hasher.matchesCalls()).isEqualTo(checksBefore + 1);
    }

    @Test
    void malformedEmailFailsAsInvalidCredentials() {
        assertThatThrownBy(() -> authenticateUser.execute(new AuthenticateCommand("no-es-un-correo", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongPasswordIsRecordedAsAFailedAttempt() {
        assertThatThrownBy(() -> authenticateUser.execute(new AuthenticateCommand("kevin@example.com", "incorrecta-123")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(users.findById(user.id()).orElseThrow().failedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void fifthFailureLocksTheAccountAndThenEvenTheRightPasswordIsRejected() {
        AuthenticateCommand wrong = new AuthenticateCommand("kevin@example.com", "incorrecta-123");
        for (int attempt = 1; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> authenticateUser.execute(wrong)).isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authenticateUser.execute(wrong)).isInstanceOf(AccountLockedException.class);
        assertThatThrownBy(() -> authenticateUser.execute(new AuthenticateCommand("kevin@example.com", PASSWORD)))
                .isInstanceOf(AccountLockedException.class);
    }
}
