package com.personaltracker.identity.application.usecase;

import static com.personaltracker.identity.application.IdentityFakes.CLOCK;
import static com.personaltracker.identity.application.IdentityFakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.application.IdentityFakes.InMemoryUserRepository;
import com.personaltracker.identity.application.dto.UpdateProfileCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.domain.exception.InvalidTimeZoneException;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateProfileTests {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final UpdateProfile updateProfile = new UpdateProfile(users, CLOCK);
    private User user;

    @BeforeEach
    void setUp() {
        user = User.register(UserId.newId(), new Email("kevin@example.com"), "hash", "Kevin", NOW);
        users.save(user);
    }

    @Test
    void updatesOnlyTheFieldsThatAreSent() {
        UserProfile renamed = updateProfile.execute(new UpdateProfileCommand(user.id(), "Kevin Jaimes", null));
        assertThat(renamed.displayName()).isEqualTo("Kevin Jaimes");
        assertThat(renamed.timeZone()).isEqualTo("America/Bogota");

        UserProfile moved = updateProfile.execute(new UpdateProfileCommand(user.id(), null, "Europe/Madrid"));
        assertThat(moved.displayName()).isEqualTo("Kevin Jaimes");
        assertThat(moved.timeZone()).isEqualTo("Europe/Madrid");
    }

    @Test
    void rejectsAnInvalidTimeZone() {
        assertThatThrownBy(() -> updateProfile.execute(new UpdateProfileCommand(user.id(), null, "+05:00")))
                .isInstanceOf(InvalidTimeZoneException.class);
    }

    @Test
    void failsWhenTheAccountNoLongerExists() {
        assertThatThrownBy(() -> updateProfile.execute(new UpdateProfileCommand(UserId.newId(), "X", null)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
