package com.personaltracker.identity.application.usecase;

import com.personaltracker.identity.application.dto.UpdateProfileCommand;
import com.personaltracker.identity.application.dto.UserProfile;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.exception.UserNotFoundException;
import com.personaltracker.identity.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cambia el nombre visible y/o la zona horaria del propio usuario. */
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
        users.save(user);
        return UserProfile.from(user);
    }
}
