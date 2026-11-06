package com.personaltracker.identity.application.usecase;

import static com.personaltracker.identity.application.IdentityFakes.CLOCK;
import static com.personaltracker.identity.application.IdentityFakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.application.IdentityFakes.FakePasswordHasher;
import com.personaltracker.identity.application.IdentityFakes.InMemoryUserRepository;
import com.personaltracker.identity.application.IdentityFakes.RecordingSessionTerminator;
import com.personaltracker.identity.application.dto.ChangePasswordCommand;
import com.personaltracker.identity.domain.exception.CurrentPasswordIncorrectException;
import com.personaltracker.identity.domain.exception.PasswordPolicyViolationException;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangePasswordTests {

    private static final String CURRENT = "contraseña-actual-1";
    private static final String NEW = "contraseña-nueva-22";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher hasher = new FakePasswordHasher();
    private final RecordingSessionTerminator sessions = new RecordingSessionTerminator();
    private final ChangePassword changePassword = new ChangePassword(users, hasher, sessions, CLOCK);
    private User user;

    @BeforeEach
    void setUp() {
        user = User.register(UserId.newId(), new Email("kevin@example.com"), hasher.hash(CURRENT), "Kevin", NOW);
        users.save(user);
    }

    @Test
    void changesThePasswordAndClosesTheOtherSessions() {
        changePassword.execute(new ChangePasswordCommand(user.id(), CURRENT, NEW, "sesion-actual"));

        assertThat(users.findById(user.id()).orElseThrow().passwordHash()).isEqualTo(hasher.hash(NEW));
        assertThat(sessions.user()).isEqualTo(user.id());
        assertThat(sessions.keptSessionId()).isEqualTo("sesion-actual");
    }

    @Test
    void rejectsAWrongCurrentPasswordWithoutTouchingSessions() {
        assertThatThrownBy(() -> changePassword.execute(
                        new ChangePasswordCommand(user.id(), "no-es-la-actual", NEW, "sesion-actual")))
                .isInstanceOf(CurrentPasswordIncorrectException.class);

        assertThat(users.findById(user.id()).orElseThrow().passwordHash()).isEqualTo(hasher.hash(CURRENT));
        assertThat(sessions.calls()).isZero();
    }

    @Test
    void rejectsANewPasswordOutsideThePolicy() {
        assertThatThrownBy(() -> changePassword.execute(
                        new ChangePasswordCommand(user.id(), CURRENT, "corta", "sesion-actual")))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThat(sessions.calls()).isZero();
    }
}
