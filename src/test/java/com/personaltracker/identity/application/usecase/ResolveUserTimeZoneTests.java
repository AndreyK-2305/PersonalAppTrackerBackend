package com.personaltracker.identity.application.usecase;

import static com.personaltracker.identity.application.IdentityFakes.CLOCK;
import static com.personaltracker.identity.application.IdentityFakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.identity.application.IdentityFakes.InMemoryUserRepository;
import com.personaltracker.identity.application.dto.UpdateProfileCommand;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ResolveUserTimeZoneTests {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final ResolveUserTimeZone timeZones = new ResolveUserTimeZone(users);

    @Test
    void returnsTheZoneOfTheProfile() {
        User user = User.register(UserId.newId(), new Email("kevin@example.com"), "hash", "Kevin", NOW);
        users.save(user);

        assertThat(timeZones.of(user.id())).isEqualTo(ZoneId.of("America/Bogota"));

        new UpdateProfile(users, CLOCK).execute(new UpdateProfileCommand(user.id(), null, "Europe/Madrid"));

        assertThat(timeZones.of(user.id())).isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void failsForAnUnknownUser() {
        assertThatThrownBy(() -> timeZones.of(UserId.newId())).isInstanceOf(UserNotFoundException.class);
    }
}
