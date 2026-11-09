package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.dto.UpdateProfileCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.PersonName;
import com.personaltracker.identity.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cambia el nombre visible, la zona horaria, los nombres o los apellidos del propio usuario. */
@Service
public class UpdateProfile {

    private final UserRepository users;
    private final Clock clock;

    public UpdateProfile(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public UserProfile execute(UpdateProfileCommand command) {
        User user = users.findById(command.owner()).orElseThrow(UserNotFoundException::new);
        Instant now = clock.instant();
        if (command.displayName() != null) {
            user.rename(command.displayName(), now);
        }
        if (command.timeZone() != null) {
            user.changeTimeZone(User.parseTimeZone(command.timeZone()), now);
        }
        if (command.givenNames() != null || command.surnames() != null) {
            PersonName current = user.personName();
            user.changePersonName(new PersonName(
                    command.givenNames() != null ? command.givenNames() : current.givenNames(),
                    command.surnames() != null ? command.surnames() : current.surnames()), now);
        }
        users.save(user);
        return UserProfile.from(user);
    }
}
